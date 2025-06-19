(ns domain-name-flow.tables
  (:require [clojure.string :as str]
            [hiccup2.core :as ht]))


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

(comment
  (frequencies-table test-values "test"))
