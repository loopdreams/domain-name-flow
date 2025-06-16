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
        body (table-rows freqs)]
    (->>
     (cons head body)
     (into [:table]))))

(defn single-box [[name count]]
  [:div {:class "tld-box"}
   [:div {:class "tld-name"} name]
   [:div {:class "tld-val"} count]])

(defn frequencies-grid [freqs label]
  [:div
   [:h3 label]
   (into [:div {:class "flex eight"}]
         (mapv single-box freqs))])



(defn sort-g-cc-tlds [db]
  (let [g-tlds (select-keys db (for [[k _] db
                                     :when (> (count k) 2)]
                                 k))
        cc-tlds (select-keys db (for [[k _] db
                                      :when (= (count k) 2)]
                                  k))]
    [(if (seq g-tlds) (frequencies-grid (reverse (sort-by val g-tlds)) "gTLDs")
         [:div "Waiting for gTLDs to appear"])
     (if (seq cc-tlds) (frequencies-grid (reverse (sort-by val cc-tlds)) "ccTLDs")
         [:div "Waiting for ccTLDs to appear"])]))

(comment
  (frequencies-table test-values "test"))
