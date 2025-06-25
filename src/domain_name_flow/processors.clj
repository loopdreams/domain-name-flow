(ns domain-name-flow.processors
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
            [domain-name-flow.tables :as tables]
            [java-time.api :as jt])
  (:import java.time.Instant))

;; Process functions for:
;; a. receiving/unpacking data stream
;;    - separate domain and tld
;; b. parsing/processing it in various ways:
;;    - add tld to frequency map
;;    - count average domain length


(defn split-url-string [^String url-string]
  (str/split url-string #"\."))

(defn split-cert-authority-string
  "Cert Authority info is in the form \"Authority 'log'\""
  [cert-string]
  (str/split cert-string #" '"))

(defn record-handler
  ([] {:ins      {:records "Channel to receive kafka records"}
       :outs     {:domains "Channel to send extracted domain names."
                  :timestamps "Channel to foward timestamps."
                  :names "Channel to forward tld/cert authority names."}
       :workload :compute})

  ([args] args)

  ([state _transition] state)

  ([state _input-id msg]
   (let [{:keys [domain
                 cert_index
                 ct_name
                 timestamp]}
         (json/read-value msg json/keyword-keys-object-mapper)
         [domain tld] (split-url-string domain)
         [c-authority _c-log] (split-cert-authority-string ct_name)]
     [state {:domains [domain]
             :timestamps [timestamp]
             :names [{:cert c-authority
                      :tld tld}]}])))

;; Average domain names length


(defn update-stats [{:keys [n-items sum _average max min] :as stats} new-domain]
  (let [n-len (count new-domain)
        n-nxt (inc n-items)
        n-sum (+ sum n-len)]
    {:n-items n-nxt
     :sum     n-sum
     :min     (if (< n-len min) n-len min)
     :max     (if (> n-len max) n-len max)
     :average (float (/ n-sum n-nxt))}))

(defn resetter
  ([] {:outs {:reset "send reset signal"}})
  ([args] args)
  ([state _tran] state)
  ([state _id _msg]
   [state nil]))

(defn domain-name-stats
  ([] {:ins  {:domains "Channel to receive domain strings"
              :push    "Channel to signal when to push to server component"
              :reset   "Channel to receive signal to reset counts"}
       :outs {:name-stats "Channel to send stat values"}})
  ([args] (assoc args :name-stats {:n-items 0
                                   :sum     0
                                   :min     1000
                                   :max     0
                                   :average 0}))
  ([state _transition] state)
  ([state id msg]
   (case id

     :domains
     [(assoc state :name-stats (update-stats (:name-stats state) msg))]

     :push
     [state {:name-stats [(:name-stats state)]}]

     :reset
     [(assoc state :name-stats {:n-items 0
                                :sum 0
                                :min 1000
                                :max 0
                                :average 0}) nil]
     [state nil])))



;; TLD frequency map


;; TODO: refactor to just send the frequency maps, and proccess them to hiccup elsewhere
(defn tld-processor
  ([] {:ins {:tlds "Channel to receive tld strings"
             :push "Channel to receive push to websocket signal"}
       :outs {:g-tld-frequencies "Channel to send gTLD frequencies as hiccup"
              :cc-tld-frequencies "Channel to send ccTLD frequencies as hiccup"}})
  ([args] (assoc args :db {}))
  ([state transition] state)
  ([state id-input msg]
   (case id-input
     :tlds
     (let [state' (update-in state [:db msg] (fnil inc 0))]
       [state' nil])
     :push
     (let [[gtld cctld] (tables/sort-g-cc-tlds (:db state))]
       [state {:g-tld-frequencies [gtld]
               :cc-tld-frequencies [cctld]}])
     [state nil])))

(defn cert-authority-processor
  ([] {:ins {:ct-name "Channel to receive the cert authority names on."
             :push "Channel to receive push to websocket signal"}
       :outs {:ct-frequencies "Channel to sent cert authority frequencies"}})
  ([args] (assoc args :db {}))
  ([state _transition] state)
  ([state id-input msg]
   (case id-input
     :ct-name
     (let [state' (update-in state [:db msg] (fnil inc 0))]
       [state' nil])
     :push
     [state {:ct-frequencies [(:db state)]}])))

(defn name-frequencies-processor
  "Takes in a name (tld/gtld/cert authority) and adds it to a frequency map.
  Pushes to server on push signal."
  ([] {:ins {:names "Channel to receive a name on."
             :push "Channel to receive push to server signal"}
       :outs {:frequencies "Channel to send name frequencies map"}})
  ([args] (assoc args
                 :tld-db (atom {})
                 :cert-db (atom {})))

  ([state _transition] state)
  ([{:keys [tld-db cert-db] :as state} input-id {:keys [cert tld]}]
   (case input-id
     :names
     (do
       (swap! tld-db update tld (fnil inc 0))
       (swap! cert-db update cert (fnil inc 0))
       [state nil])
     :push
     [state {:frequencies [{:tlds @tld-db
                            :certs @cert-db}]}])))

;; Scheduler - schedule push to websocket (down the line)

(defn scheduler
  ([] {:outs {:push "Channel to send push signal"}
       :params {:wait "Scheduler frequency"}})

  ([args] (assoc args
                 ::flow/in-ports {:alarm (a/chan 10)}
                 :stop (atom false)))

  ([{:keys [wait ::flow/in-ports] :as state} transition]
   (case transition

     ::flow/resume
     (let [stop-atom (atom false)]
       (future (loop []
                 (let [put (a/>!! (:alarm in-ports) true)]
                   (when (and put (not @stop-atom))
                     (^ [long] Thread/sleep wait)
                     (recur)))))
       (assoc state :stop stop-atom))

     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ([state in _msg]
   [state (when (= in :alarm) {:push [true]})]))

(defn scheduler-2
  ([] {:outs {:push "Channel to send push signal"}
       :params {:wait "Scheduler frequency"}})

  ([args] (assoc args
                 ::flow/in-ports {:alarm (a/chan 10)}
                 :stop (atom false)))

  ([{:keys [wait ::flow/in-ports] :as state} transition]
   (case transition

     ::flow/resume
     (let [stop-atom (atom false)]
       (future (loop []
                 (let [put (a/>!! (:alarm in-ports) true)]
                   (when (and put (not @stop-atom))
                     (^ [long] Thread/sleep wait)
                     (recur)))))
       (assoc state :stop stop-atom))

     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ([state in _msg]
   [state (when (= in :alarm) {:push [true]})]))


;; Announce Rate
;; Two ways to think about it:
;; - an independant clock that counts how many received on a channel ever x time
;; - Use the timestamps sent with the domains, count time diff per x domains
;; - Maybe do both, and see the difference?

(defn rate-calculator-timestamps
  ([] {:outs {:t-stamp-rate "Channel to sent the timestamp rate on."}
       :ins {:timestamps "Channel to receive domain name broadcast timestamps on."}
       :params {:batch-size "Number of domains to group by"}})
  ([args] (assoc args :batch (atom [])))
  ([state _transition] state)
  ([state _in msg]
   (let [cur @(:batch state)]
     (if (= (dec (:batch-size state)) (count cur))
       (let [cur (conj cur msg)
             min (apply min cur)
             max (apply max cur)
             time-diff-seconds (- max min)
             rate (float (/ (:batch-size state) time-diff-seconds))
             message (format "%.2f domains received every second" rate)]
         (do
           (reset! (:batch state) [])
           [state {:t-stamp-rate [message]}]))
       (do
         (swap! (:batch state) conj msg)
         [state nil])))))


;; Hourly domain counts

(defn timestamp->entry [timestamp unit]
  (let [dt (jt/local-date-time (Instant/ofEpochSecond timestamp) (jt/zone-id "UTC"))]
    (case unit
      :hour  (jt/as dt :year :month-of-year :day-of-month :hour-of-day)
      :day   (jt/as dt :year :month-of-year :day-of-month)
      :month (jt/as dt :year :month-of-year))))

(defn counts-by-time
  ([] {:ins {:timestamps "Channel to receive timestamp info on"
             :push "Push to server signal"}
       :outs {:time-counts "Channel to send time counts to server"}
       :params {:time-unit "Unit of time to group counts: :hour, :day, or :month"}})
  ([args] (assoc args :db (atom {})))
  ([state _transition] state)
  ([state in msg]
   (case in
     :timestamps
     (let [entry (timestamp->entry msg (:time-unit state))]
       (do (swap! (:db state) update-in entry (fnil inc 0))
           [state nil]))
     :push
     [state {:time-counts [@(:db state)]}])))
