(ns domain-name-flow.flow
  (:require [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
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
     :tld-db                     {:args {}
                                  :proc (flow/process #'processors/tld-processor)}
     :avgs-scheduler             {:args {:wait 1000}
                                  :proc (flow/process #'processors/scheduler)}
     ;; :domain-len-avgs            {:args {}
     ;;                              :proc (flow/process #'processors/domain-length-averager)}
     :domain-name-stats          {:args {}
                                  :proc (flow/process #'processors/domain-name-stats)}
     :rate-calculator-timestamps {:args {:batch-size 100}
                                  :proc (flow/process #'processors/rate-calculator-timestamps)}
     :webserver                  {:args {}
                                  :proc (flow/process #'server/webserver)}}
    :conns [[[:generator :out] [:record-handler :records]]
            [[:record-handler :tlds] [:tld-db :tlds]]
            [[:record-handler :domains] [:domain-name-stats :domains]]
            [[:record-handler :timestamps] [:rate-calculator-timestamps :timestamps]]
            [[:avgs-scheduler :push] [:domain-name-stats :push]]
            [[:avgs-scheduler :push] [:tld-db :push]]
            [[:domain-name-stats :name-stats] [:webserver :name-stats]]
            [[:tld-db :g-tld-frequencies] [:webserver :g-tld-frequencies]]
            [[:tld-db :cc-tld-frequencies] [:webserver :cc-tld-frequencies]]
            [[:rate-calculator-timestamps :t-stamp-rate] [:webserver :t-stamp-rate]]]}))



(comment
  (def f (create-flow))
  (def chs (flow/start f))
  (flow/resume f)
  (flow/pause f)
  (flow/stop f)




  (def server (mon/start-server {:flow f}))
  (mon/stop-server server))
