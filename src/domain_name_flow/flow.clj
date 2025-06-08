(ns domain-name-flow.flow
  (:require [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
            [clojure.core.async.flow-monitor :as mon]
            [domain-name-flow.utils :as u]))


(defn source
  "Source process for random urls"
  ;; Description
  ([] {:params {:wait "Time in ms to wait between generating"}
       :outs {:out "Output channel for urls"}})

  ;; Init
  ([args]
   (assoc args
          ::flow/in-ports {:urls (a/chan 100)}
          :stop (atom false)))

  ;; Transition
  ([{:keys [wait ::flow/in-ports] :as state} transition]
   (case transition
     ::flow/resume
     (let [stop-atom (atom false)]
       (future (u/url-generator (:urls in-ports) wait stop-atom))
       (assoc state :stop stop-atom))
     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; Transform
  ([state in msg]
   [state (when (= in :urls) {:out [msg]})]))

(defn url-splitter
  ([] {:ins {:urls "Channel to recieve url strings"}
       :outs {:tlds "Channel to send extracted tlds"
              :domains "Channel to send extracted domain names."}
       :workload :compute})
  ([args] (-> args (assoc :tlds []) (assoc :domains [])))
  ([state transition] state)
  ([state input-id msg]
   (let [[domain tld] (u/split-url-string msg)]
     [state {:tlds [tld]
             :domains [domain]}])))

(defn in-memory-tld-db
  ([] {:ins {:tlds "Channel to recieve tld strings"}})
  ([args] (assoc args :db {}))
  ([state transition] state)
  ([state _ msg]
   (let [state' (update-in state [:db msg] (fnil inc 0))]
     (println state')
     [state' nil])))

(defn domain-length-averager
  ([] {:ins {:domains "Channel to recieve domain strings"}})
  ([args] (assoc args :average-data {:n-items 0
                                     :sum 0
                                     :average 0}))
  ([state transition] state)
  ([state _id msg]
   [(assoc state :average-data (u/update-average (:average-data state)
                                               msg))
    nil]))

(comment
  (let [state {}
        [state' msgs'] (url-splitter state :urls "helloworld.com")]
    msgs'))

(defn create-flow
  []
  (flow/create-flow
   {:procs {:generator {:args {:wait 500} :proc (flow/process #'source)}
            :url-splitter {:args {} :proc (flow/process #'url-splitter)}
            :tld-db {:args {} :proc (flow/process #'in-memory-tld-db)}
            :domain-len-avgs {:args {} :proc (flow/process #'domain-length-averager)}}
    :conns [[[:generator :out] [:url-splitter :urls]]
            [[:url-splitter :tlds] [:tld-db :tlds]]
            [[:url-splitter :domains] [:domain-len-avgs :domains]]]}))

(comment
  (def f (create-flow))
  (def chs (flow/start f))
  (flow/resume f)
  (flow/pause f)
  (flow/stop f)



  (def server (mon/start-server {:flow f}))
  (mon/stop-server server))
