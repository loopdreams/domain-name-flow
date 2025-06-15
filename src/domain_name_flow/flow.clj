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
    {:generator                  {:args {:server-url "kafka.zonestream.openintel.nl:9092"
                                         :topic      "newly_registered_domain"
                                         ;; The 'wait' here is for when using the test url generator
                                         ;; TODO: delete later
                                         :wait       500}
                                  :proc (flow/process #'kafka/source)}
     :record-handler             {:args {}
                                  :proc (flow/process #'processors/record-handler)}
     :tld-db                     {:args {}
                                  :proc (flow/process #'processors/in-memory-tld-db)}
     :avgs-scheduler             {:args {:wait 1000}
                                  :proc (flow/process #'processors/scheduler)}
     :domain-len-avgs            {:args {}
                                  :proc (flow/process #'processors/domain-length-averager)}
     :rate-calculator-timestamps {:args {:batch-size 100}
                                  :proc (flow/process #'processors/rate-calculator-timestamps)}
     :webserver                  {:args {}
                                  :proc (flow/process #'server/webserver)}}
    :conns [[[:generator :out] [:record-handler :records]]
            [[:record-handler :tlds] [:tld-db :tlds]]
            [[:record-handler :domains] [:domain-len-avgs :domains]]
            [[:record-handler :timestamps] [:rate-calculator-timestamps :timestamps]]
            [[:avgs-scheduler :push] [:domain-len-avgs :push]]
            [[:avgs-scheduler :push] [:tld-db :push]]
            [[:domain-len-avgs :averages] [:webserver :averages]]
            [[:tld-db :tld-frequencies] [:webserver :tld-frequencies]]
            [[:rate-calculator-timestamps :t-stamp-rate] [:webserver :t-stamp-rate]]]}))



(comment
  (def f (create-flow))
  (def chs (flow/start f))
  (flow/resume f)
  (flow/pause f)
  (flow/stop f)




  (def server (mon/start-server {:flow f}))
  (mon/stop-server server))
