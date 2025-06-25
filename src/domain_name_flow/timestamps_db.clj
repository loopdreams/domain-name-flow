(ns domain-name-flow.timestamps-db
  (:require [java-time.api :as jt]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [jsonista.core :as json])
  (:import java.time.Instant))


;; Persistently track timestamp data, to help build a picture of domain name registrations over time.
;; Count hourly timestamp data, in a table with :year :month :day :hour :count
;; For current hour, live count the timestamps, then save them to db at end of every hour and restart count

(def db {:dbtype "sqlite"
         :dbname "db/domain_name_flow.db"})

(def ds (jdbc/get-datasource db))

(comment
  (jdbc/execute! ds ["
create table timestamp_counts (
  date int not null,
  count int not null
)"]))

(comment
  (sql/insert! ds "timestamp_counts" {:date 2025062512 :count 982}))

(defn add-to-db! [ds {:keys [date count] :as data}]
  (sql/insert! ds "timestamp_counts" {:date date :count count}))

(def db-date-key-format "yyyyMMddHH")

(defn timestamp->dt [ts]
  (jt/local-date-time (Instant/ofEpochSecond ts) (jt/zone-id "UTC")))

(defn encode-db-key [dt]
  (jt/format db-date-key-format dt))

(defn parse-db-key [long]
  (->> long
       str
       (jt/local-date-time db-date-key-format)
       (jt/format :iso-date-time)))

(comment
  (parse-db-key 2024060606))


(jt/as (timestamp->dt 1750852815) :hour-of-day)

(defn timestamps-manager
  ([] {:ins {:timestamps "Channel"}
       :outs {:db-data "Channel to send data for writing to db"}})
  ([args] args)
  ([state _transition] state)
  ([{:keys [current-day current-hour current-count] :as state} _in msg]
   (let [dt (timestamp->dt msg)
         dy (jt/as dt :day-of-month)
         hr (jt/as dt :hour-of-day)]
     (cond
       (not current-day)
       [(-> state
            (assoc :ref-timestamp dt)
            (assoc :current-day dy)
            (assoc :current-hour hr)
            (assoc :current-count 1)) nil]

       (> dy current-day)
       [(-> state
            (assoc :ref-timestamp dt)
            (assoc :current-day dy)
            (assoc :current-hour hr)
            (assoc :curent-count 1)
            {:db-data [{:date (encode-db-key (:ref-timestamp state))
                        :count current-count}]})]

       (> hr current-hour)
       [(-> state
            (assoc :ref-timestamp dt)
            (assoc :current-hour hr)
            (assoc :current-count 1))
        {:db-data [{:date (encode-db-key (:ref-timestamp state))
                    :count current-count}]}]
       :else
       [(update state :current-count inc) nil]))))

(defn timestamp-db-writer
  ([] {:ins {:db-data "channel to receive data to write. Expects {:date x :count x}"}
       :workload :io})
  ([args] (assoc args :ds ds))
  ([state _transition] state)
  ([{:keys [ds] :as state} _in msg]
   (when (seq msg)
     (add-to-db! ds msg))
   [state nil]))


(defn echart-spec [data-series]
  {:xAxis {:type "time"}
   :yAxis {}
   :series {:type "line"
            :data data-series}})


(defn echart-spec-create [ds]
  (let [data (sql/query ds ["select * from timestamp_counts"])
        data-series-tuples (mapv (fn [{:timestamp_counts/keys [date count]}]
                                   [(parse-db-key date) count])
                                 data)]
    (echart-spec data-series-tuples)))

(def echart-chart (echart-spec-create ds))
(comment
  (echart-spec-create ds))
