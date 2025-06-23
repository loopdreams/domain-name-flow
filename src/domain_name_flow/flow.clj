(ns domain-name-flow.flow
  (:gen-class)
  (:require [clojure.core.async.flow :as flow]
            [clojure.core.async.flow-monitor :as mon]
            [domain-name-flow.server :as server]
            [domain-name-flow.processors :as processors]
            [domain-name-flow.kafka-intake :as kafka]
            [domain-name-flow.url-generator :as tester]))

(defn create-flow
  []
  (flow/create-flow
   {:procs
    {:record-handler             {:args {}
                                  :proc (flow/process #'processors/record-handler)}
     ;; :generator                  {:args {:server-url "kafka.zonestream.openintel.nl:9092"
     ;;                                     :topic      "newly_registered_domain"
     ;;                                     ;; The 'wait' here is for when using the test url generator
     ;;                                     ;; TODO: delete later
     ;;                                     :wait       500}
     ;;                              :proc (flow/process #'tester/test-source)}
     :generator                  {:args {:server-url "kafka.zonestream.openintel.nl:9092"
                                         :topic      "newly_registered_domain"
                                         ;; The 'wait' here is for when using the test url generator
                                         ;; TODO: delete later
                                         :wait       500}
                                  :proc (flow/process #'kafka/source)}
     :frequencies-store          {:args {}
                                  :proc (flow/process #'processors/name-frequencies-processor)}
     :scheduler                  {:args {:wait 1000}
                                  :proc (flow/process #'processors/scheduler)}
     :scheduler-2                {:args {:wait 2000}
                                  :proc (flow/process #'processors/scheduler-2)}
     :domain-name-stats          {:args {}
                                  :proc (flow/process #'processors/domain-name-stats)}
     :rate-calculator-timestamps {:args {:batch-size 10}
                                  :proc (flow/process #'processors/rate-calculator-timestamps)}
     :timestamp-counts           {:args {:time-unit :hour}
                                  :proc (flow/process #'processors/counts-by-time)}
     :webserver                  {:args {}
                                  :proc (flow/process #'server/webserver)}}
    :conns [[[:generator :out]                           [:record-handler :records]]
            [[:record-handler :domains]                  [:domain-name-stats :domains]]
            [[:record-handler :timestamps]               [:rate-calculator-timestamps :timestamps]]
            [[:record-handler :timestamps]               [:timestamp-counts :timestamps]]
            [[:record-handler :names]                    [:frequencies-store :names]]
            [[:scheduler :push]                          [:domain-name-stats :push]]
            [[:scheduler :push]                          [:frequencies-store :push]]
            [[:scheduler-2 :push]                        [:timestamp-counts :push]]
            [[:domain-name-stats :name-stats]            [:webserver :name-stats]]
            [[:frequencies-store :frequencies]           [:webserver :frequencies]]
            [[:timestamp-counts :time-counts]            [:webserver :time-counts]]
            [[:rate-calculator-timestamps :t-stamp-rate] [:webserver :t-stamp-rate]]]}))

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
