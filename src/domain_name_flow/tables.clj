(ns domain-name-flow.tables
  (:require [clojure.string :as str]
            [hiccup2.core :as ht]))

(def test-values
  {:a 3
   :b 5
   :c 6})

(def max-width 100)

;; 9633 = □
(def bar-symbol (char 9633))

(defn bar-string [value max-value]
  (let [len (* max-width (/ value max-value))]
    (str/join (repeat len bar-symbol))))

(defn table-head [label]
  [:tr
   [:th label]
   [:th "Count"]
   [:th]])

(defn table-rows [freqs]
  (let [max (apply max (vals freqs))]
    (mapv (fn [[name value]]
            [:tr
             [:td name]
             [:td value]
             [:td (bar-string value max)]])
          freqs)))


(defn frequencies-table [freqs label]
  (let [head (table-head label)
        body (table-rows (sort freqs))]
    (->>
     (cons head body)
     (into [:table]))))

(comment
  (frequencies-table test-values "test"))
