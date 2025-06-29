(ns domain-name-flow.timestamps-db
  (:require [java-time.api :as jt]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [jsonista.core :as json])
  (:import java.time.Instant))

;; Persistently track timestamp data, to help build a picture of domain name registrations over time.
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

(defn db-init []
  (jdbc/execute! ds ["
create table timestamp_counts (
  date int not null,
  count int not null
) "]))


(comment
;;; 1152

  (defn filter-valid [n]
    (let [s (str n)
          hr (parse-long (subs s 8 10))
          dy (parse-long (subs s 6 8))]
      (and (> 25 hr)
           (< 0 dy 32))))
  (doseq [n (filter filter-valid (range 2025061010 2025062512))]
    (sql/insert! ds "timestamp_counts" {:date n :count (rand-nth (range 4000 6000))}))
  (count (sql/query ds ["select * from timestamp_counts"])))

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

(defn timestamps-manager
  ([] {:ins  {:timestamps "Channel to receive timestamps"
              :push       "Channel to receive push signal"}
       :outs {:db-data      "Channel to send data for writing to db"
              :hourly-count "Channel to send count for current hour. Sends on push"
              :backup-signal "Signal for other processes to also write data (hourly)"}})
  ([args] args)
  ([state _transition] state)
  ([{:keys [current-day current-hour current-count] :as state} in msg]
   (case in
     :push [state (when current-count {:hourly-count [current-count]})]
     :timestamps
     (let [dt                (timestamp->dt msg)
           dy                (jt/as dt :day-of-month)
           hr                (jt/as dt :hour-of-day)
           update-state-hour (fn [state dt hr]
                               (-> state
                                   (assoc :ref-timestamp dt)
                                   (assoc :current-hour hr)
                                   (assoc :current-count 1)))
           update-state      (fn [state dt dy hr]
                               (-> state
                                   (update-state-hour dt hr)
                                   (assoc :current-day dy)))]
       (cond
         (not current-day)
         ;; Init
         [(update-state state dt dy hr)
          nil]

         (> dy current-day)
         [(update-state state dt dy hr)
          {:db-data [{:date  (encode-db-key (:ref-timestamp state))
                      :count current-count}]}]

         (> hr current-hour)
         [(update-state-hour state dt hr)
          {:db-data [{:date  (encode-db-key (:ref-timestamp state))
                      :count current-count}]
           :backup-signal [:backup-now]}]
         :else
         [(update state :current-count inc) nil])))))

(defn timestamp-db-writer
  ([] {:ins {:db-data "channel to receive data to write. Expects {:date x :count x}"}
       :workload :io})
  ([args] (do (db-init) (assoc args :ds ds)))
  ([state _transition] state)
  ([{:keys [ds] :as state} _in msg]
   (when (seq msg)
     (add-to-db! ds msg))
   [state nil]))


;; Chart data

(defn twentyfour-hr-window [last-entry]
  (-> (jt/local-date-time (first last-entry))
      (jt/minus (jt/days 1))
      str))

(defn timestamp-counts-tuples []
  (let [data (sql/query ds ["select * from timestamp_counts"])
        series (mapv (fn [{:timestamp_counts/keys [date count]}]
                       [(parse-db-key date) count])
                     data)]
    {:series series
     :offset (twentyfour-hr-window (last series))}))


#_(defn some-ds? [ds]
    (< 0 (count (sql/query ds ["select * from timestamp_counts"]))))

(comment
  (some-ds? ds)
  (count (sql/query ds ["select * from timestamp_counts"])))
