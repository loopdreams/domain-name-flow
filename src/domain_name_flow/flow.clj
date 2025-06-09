(ns domain-name-flow.flow
  (:require [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
            [clojure.core.async.flow-monitor :as mon]
            [jsonista.core :as json]
            [domain-name-flow.kafka-intake :as k]
            [domain-name-flow.utils :as u]))


#_(defn source
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

(defn source
  "Source process for random urls"
  ;; Description
  ([] {:params {:server-url "URL address for Kafka stream"
                :topic "Kafka topic name"}
       :outs {:out "Output channel for urls"}})

  ;; Init
  ([args]
   (assoc args
          ::flow/in-ports {:records (a/chan 100)}
          :stop (atom false)))

  ;; Transition
  ([{:keys [server-url topic ::flow/in-ports] :as state} transition]
   (case transition
     ::flow/resume
     (let [stop-atom (atom false)]
       (future (k/kafka-consumer (:records in-ports) stop-atom server-url topic))
       (assoc state :stop stop-atom))
     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; Transform
  ([state in msg]
   [state (when (= in :records) {:out [msg]})]))

(comment
  (json/read-value "{\"domain\": \"prataptravel.com.\", \"cert_index\": 1654682859, \"ct_name\": \"DigiCert Yeti2025 Log\", \"timestamp\": 1748548424}"
                   json/keyword-keys-object-mapper))

(defn record-handler
  ([] {:ins {:records "Channel to recieve kafka records"}
       :outs {:tlds "Channel to send extracted tlds"
              :domains "Channel to send extracted domain names."}
       :workload :compute})
  ([args] args)
  ([state transition] state)
  ([state _input-id msg]
   (let [{:keys [domain
                 cert_index
                 ct_name
                 timestamp]}
         (json/read-value msg json/keyword-keys-object-mapper)
         [domain tld] (u/split-url-string domain)]
     [state {:tlds [tld]
             :domains [domain]}])))

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

#_(defn create-flow
    []
    (flow/create-flow
     {:procs {:generator {:args {:wait 500} :proc (flow/process #'source)}
              :url-splitter {:args {} :proc (flow/process #'url-splitter)}
              :tld-db {:args {} :proc (flow/process #'in-memory-tld-db)}
              :domain-len-avgs {:args {} :proc (flow/process #'domain-length-averager)}}
      :conns [[[:generator :out] [:url-splitter :urls]]
              [[:url-splitter :tlds] [:tld-db :tlds]]
              [[:url-splitter :domains] [:domain-len-avgs :domains]]]}))

(defn create-flow
  []
  (flow/create-flow
   {:procs {:generator {:args {:server-url "kafka.zonestream.openintel.nl:9092"
                               :topic "newly_registered_domain"}
                        :proc (flow/process #'source)}
            :record-handler {:args {} :proc (flow/process #'record-handler)}
            :tld-db {:args {} :proc (flow/process #'in-memory-tld-db)}
            :domain-len-avgs {:args {} :proc (flow/process #'domain-length-averager)}}
    :conns [[[:generator :out] [:record-handler :records]]
            [[:record-handler :tlds] [:tld-db :tlds]]
            [[:record-handler :domains] [:domain-len-avgs :domains]]]}))

(comment
  (def f (create-flow))
  (def chs (flow/start f))
  (flow/resume f)
  (flow/pause f)
  (flow/stop f)



  (def server (mon/start-server {:flow f}))
  (mon/stop-server server))
