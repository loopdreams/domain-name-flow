(ns domain-name-flow.tables
  (:require [clojure.string :as str]
            [java-time.api :as jt]
            [jsonista.core :as json]
            [hiccup2.core :as h]
            [domain-name-flow.timestamps-db :refer [ds]]
            [next.jdbc.sql :as sql]))


(defn single-box [[name count]]
  [:div {:class "tld-box"}
   [:div {:class "tld-name"} name]
   [:div {:class "tld-val"} (format "%,12d" count)]])

(defn frequencies-grid [freqs]
  (into [:div {:class "grid grid-cols-3 md:grid-cols-5"}]
        (mapv single-box freqs)))

(defn sort-g-cc-tlds [db]
  (let [g-tlds (select-keys db (for [[k _] db
                                     :when (> (count k) 2)]
                                 k))
        cc-tlds (select-keys db (for [[k _] db
                                      :when (= (count k) 2)]
                                  k))]
    [(if (seq g-tlds) (frequencies-grid (reverse (sort-by val g-tlds)))
         [:div "Waiting for gTLDs to appear"])
     (if (seq cc-tlds) (frequencies-grid (reverse (sort-by val cc-tlds)))
         [:div "Waiting for ccTLDs to appear"])]))

;; Certs db in format {:authority {:log1 n :log2 n ...}}

(defn sort-by-val-reverse [m] (reverse (sort-by val m)))

(defn sort-certs-db [db]
  (let [auth-frequencies (reduce (fn [res [auth logs]]
                                   (assoc res auth (apply + (vals logs))))
                                 {} db)
        log-frequencies (reduce (fn [res [auth logs]]
                                  (reduce (fn [res [k v]]
                                            (assoc res (str k " (" auth ")") v))
                                          res logs))
                                {} db)]
    (mapv (comp frequencies-grid sort-by-val-reverse)
          [auth-frequencies log-frequencies])))
