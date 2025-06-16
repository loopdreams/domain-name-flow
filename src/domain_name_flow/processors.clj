(ns domain-name-flow.processors
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
            [domain-name-flow.tables :as tables]))

;; Process functions for:
;; a. recieving/unpacking data stream
;;    - separate domain and tld
;; b. parsing/processing it in various ways:
;;    - add tld to frequency map
;;    - count average domain length


(defn split-url-string [^String url-string]
  (str/split url-string #"\."))

(defn record-handler
  ([] {:ins      {:records "Channel to recieve kafka records"}
       :outs     {:tlds    "Channel to send extracted tlds"
                  :domains "Channel to send extracted domain names."
                  :timestamps "Channel to foward timestamps."}
       :workload :compute})

  ([args] args)

  ([state _transition] state)

  ([state _input-id msg]
   (let [{:keys [domain
                 cert_index
                 ct_name
                 timestamp]}
         (json/read-value msg json/keyword-keys-object-mapper)
         [domain tld] (split-url-string domain)]
     [state {:tlds    [tld]
             :domains [domain]
             :timestamps [timestamp]}])))


;; Average domain names length

(defn update-average [{:keys [n-items sum average] :as avgs} new-domain]
  (let [nxt-sum (+ sum (count new-domain))
        nxt-n (inc n-items)
        nxt-avg (float (/ nxt-sum nxt-n))]
    {:n-items nxt-n
     :sum nxt-sum
     :average nxt-avg}))



(defn domain-length-averager
  ([] {:ins {:domains "Channel to recieve domain strings"
             :push "Channel to signal when to push to server component"}
       :outs {:averages "Channel to send avg values"}})
  ([args] (assoc args :average-data {:n-items 0
                                     :sum 0
                                     :average 0}))
  ([state _transition] state)
  ([state id msg]
   (case id

     :domains
     [(assoc state :average-data (update-average (:average-data state) msg))]

     :push
     [state {:averages [(-> state :average-data :average)]}]

     [state nil])))

(defn update-stats [{:keys [n-items sum _average max min] :as stats} new-domain]
  (let [n-len (count new-domain)
        n-nxt (inc n-items)
        n-sum (+ sum n-len)]
    {:n-items n-nxt
     :sum     n-sum
     :min     (if (< n-len min) n-len min)
     :max     (if (> n-len max) n-len max)
     :average (float (/ n-sum n-nxt))}))

(defn domain-name-stats
  ([] {:ins  {:domains "Channel to recieve domain strings"
              :push    "Channel to signal when to push to server component"}
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

     [state nil])))



;; TLD frequency map

(defn in-memory-tld-db
  ([] {:ins {:tlds "Channel to recieve tld strings"
             :push "Channel to recieve push to websocket signal"}
       :outs {:tld-frequencies "Channel to send tld frequencies as hiccup"}})
  ([args] (assoc args :db {}))
  ([state transition] state)
  ([state id-input msg]
   (case id-input
     :tlds
     (let [state' (update-in state [:db msg] (fnil inc 0))]
       [state' nil])
     :push
     (let [hic (tables/frequencies-table (:db state) "TLD")]
       [state {:tld-frequencies [hic]}])
     [state nil])))

(defn tld-processor
  ([] {:ins {:tlds "Channel to recieve tld strings"
             :push "Channel to recieve push to websocket signal"}
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


;; Announce Rate
;; Two ways to think about it:
;; - an independant clock that counts how many recieved on a channel ever x time
;; - Use the timestamps sent with the domains, count time diff per x domains
;; - Maybe do both, and see the difference?

(defn rate-calculator-timestamps
  ([] {:outs {:t-stamp-rate "Channel to sent the timestamp rate on."}
       :ins {:timestamps "Channel to recieved domain name broadcast timestamps on."}
       :params {:batch-size "Number of domains to group by"}})
  ([args] (assoc args :batch (atom [])))
  ([state transition] state)
  ([state in msg]
   (let [cur @(:batch state)]
     (if (= (dec (:batch-size state)) (count cur))
       (let [cur (conj cur msg)
             min (apply min cur)
             max (apply max cur)
             time-diff-seconds (- max min)
             rate (float (/ (:batch-size state) time-diff-seconds))
             message (format "%.2f domains recieved every second" rate)]
         (do
           (reset! (:batch state) [])
           [state {:t-stamp-rate [message]}]))
       (do
         (swap! (:batch state) conj msg)
         [state nil])))))
