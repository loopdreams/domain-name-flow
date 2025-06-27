(ns domain-name-flow.flow
  (:gen-class)
  (:require [clojure.core.async.flow :as flow]
            [clojure.core.async.flow-monitor :as mon]
            [domain-name-flow.server :as server]
            [domain-name-flow.processors :as processors]
            [domain-name-flow.timestamps-db :as ts]
            [domain-name-flow.kafka-intake :as kafka]
            [domain-name-flow.url-generator :as tester]))

(defn create-flow
  []
  (flow/create-flow
   {:procs
    {:record-handler             {:args {}
                                  :proc (flow/process #'processors/record-handler)}
     :generator                  {:args {:server-url "kafka.zonestream.openintel.nl:9092"
                                         :topic      "newly_registered_domain"
                                         ;; The 'wait' here is for when using the test url generator
                                         ;; TODO: delete later
                                         :wait       500}
                                  :proc (flow/process #'tester/test-source)}
     ;; :generator                  {:args {:server-url "kafka.zonestream.openintel.nl:9092"
     ;;                                     :topic      "newly_registered_domain"
     ;;                                     ;; The 'wait' here is for when using the test url generator
     ;;                                     ;; TODO: delete later
     ;;                                     :wait       500}
     ;;                              :proc (flow/process #'kafka/source)}
     :frequencies-store          {:args {:backup-file "db/name_frequencies.edn"}
                                  :proc (flow/process #'processors/name-frequencies-processor)}
     :scheduler                  {:args {:wait 1000}
                                  :proc (flow/process #'processors/scheduler)}
     :domain-name-stats          {:args {:backup-file "db/name_stats.edn"}
                                  :proc (flow/process #'processors/domain-name-stats)}
     :timestamp-manager          {:args {}
                                  :proc (flow/process #'ts/timestamps-manager)}
     :timestamp-db               {:args {}
                                  :proc (flow/process #'ts/timestamp-db-writer)}
     :webserver                  {:args {:port 3000}
                                  :proc (flow/process #'server/webserver)}}
    :conns [[[:generator :out]                           [:record-handler :records]]
            [[:record-handler :domains]                  [:domain-name-stats :domains]]
            [[:record-handler :timestamps]               [:timestamp-manager :timestamps]]
            [[:record-handler :names]                    [:frequencies-store :names]]
            [[:scheduler :push]                          [:domain-name-stats :push]]
            [[:scheduler :push]                          [:frequencies-store :push]]
            [[:scheduler :push]                          [:timestamp-manager :push]]
            [[:timestamp-manager :db-data]               [:timestamp-db :db-data]]
            [[:timestamp-manager :backup-signal]         [:frequencies-store :backup]]
            [[:timestamp-manager :backup-signal]         [:domain-name-stats :backup]]
            [[:timestamp-manager :hourly-count]          [:webserver :hourly-count]]
            [[:domain-name-stats :name-stats]            [:webserver :name-stats]]
            [[:frequencies-store :frequencies]           [:webserver :frequencies]]]}))

(defn -main [& args]
  (let [f (create-flow)
        _ (flow/start f)]
    (future (flow/resume f))))


(comment
  (def f (create-flow))
  (def chs (flow/start f))
  (flow/resume f)
  (flow/pause f)
  (flow/stop f)


  (def server (mon/start-server {:flow f}))
  (mon/stop-server server))
