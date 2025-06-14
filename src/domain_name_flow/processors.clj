(ns domain-name-flow.processors
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]))

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
                  :domains "Channel to send extracted domain names."}
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
             :domains [domain]}])))


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

(defn avg-scheduler
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

;; TLD frequency map

(defn in-memory-tld-db
  ([] {:ins {:tlds "Channel to recieve tld strings"}})
  ([args] (assoc args :db {}))
  ([state transition] state)
  ([state _ msg]
   (let [state' (update-in state [:db msg] (fnil inc 0))]
     [state' nil])))
