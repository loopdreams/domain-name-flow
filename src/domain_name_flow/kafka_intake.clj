(ns domain-name-flow.kafka-intake
  (:require [clojure.core.async :as a])
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

(defn kafka-consumer [chan stop-atom server-url topic]
  (let [consumer (build-consumer server-url)]
    (.subscribe consumer [topic])
    (println (str "Subscribed to Kafka topic " topic " from " server-url))
    (while (not @stop-atom)
      (doseq [record (.poll consumer (Duration/ofMillis 1000))]
        (a/>!! chan (.value record))))))

(comment
  (let [stop-atom (atom false)
        chan (a/chan 100)]
    (future (kafka-consumer chan stop-atom
                            "kafka.zonestream.openintel.nl:9092"
                            "newly_registered_domain"))
    (println (a/<!! chan))
    (reset! stop-atom true)))


(comment
  (let [consumer (build-consumer "kafka.zonestream.openintel.nl:9092")]
    (.subscribe consumer ["newly_registered_domain"])
    (while true
      (doseq [record (.poll consumer (Duration/ofMillis 1000))]
        (println record))))
  (let [consumer (build-consumer "kafka.zonestream.openintel.nl:9092")
        stop (atom false)]
    (.subscribe consumer ["newly_registered_domain"])
    (while (not @stop)
      (doseq [record (.poll consumer (Duration/ofMillis 1000))]
        (println record)
        (reset! stop true))))
  (let [consumer (build-consumer "kafka.zonestream.openintel.nl:9092")]
    (.subscribe consumer ["newly_registered_domain"])
    (take 1
          (map identity (.poll consumer (Duration/ofMillis 1000))))))
