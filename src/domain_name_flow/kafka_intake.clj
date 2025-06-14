(ns domain-name-flow.kafka-intake
  (:require [clojure.core.async :as a]
            [clojure.core.async.flow :as flow])
  (:import org.apache.kafka.clients.consumer.KafkaConsumer
           (java.time Duration)
           (org.apache.kafka.common.serialization StringDeserializer)))

(defn build-consumer
  [bootstrap-server]
  (let [consumer-props
        {"bootstrap.servers",  bootstrap-server
         "group.id",           "example"
         "key.deserializer",   StringDeserializer
         "value.deserializer", StringDeserializer
         "auto.offset.reset",  "latest"
         "enable.auto.commit", "true"}]
    (KafkaConsumer. consumer-props)))

(defn create-consumer [server-url topic]
  (let [consumer (build-consumer server-url)]
    (.subscribe consumer [topic])
    consumer))

(defn run-consumer [chan stop-atom consumer]
  (while (not @stop-atom)
    (doseq [record (.poll consumer (Duration/ofMillis 100))]
      (a/>!! chan (.value record)))))


(comment
  (let [stop-atom (atom false)
        chan (a/chan 100)
        con (create-consumer "kafka.zonestream.openintel.nl:9092"
                             "newly_registered_domain")]
    (future (run-consumer chan stop-atom con))
    (println (a/<!! chan))
    (reset! stop-atom true)))


(defn source
  "Source process for Kafka stream"
  ;; Description
  ([] {:params {:server-url "URL address for Kafka stream"
                :topic      "Kafka topic name"}
       :outs   {:out "Output channel for urls"}})

  ;; Init
  ([{:keys [server-url topic] :as args}]
   (assoc args
          ::flow/in-ports {:records (a/chan 100)}
          :stop (atom false)
          :consumer (create-consumer server-url topic)))

  ;; Transition
  ([{:keys [consumer ::flow/in-ports] :as state} transition]
   (case transition

     ::flow/resume
     (let [stop-atom (atom false)]
       (future (run-consumer (:records in-ports) stop-atom consumer))
       (assoc state :stop stop-atom))

     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; Transform
  ([state in msg]
   [state (when (= in :records) {:out [msg]})]))
