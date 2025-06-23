(ns domain-name-flow.tables
  (:require [clojure.string :as str]
            [java-time.api :as jt]
            [hiccup2.core :as h]))


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


(def max-width 20)

;; 9633 = □
(def bar-symbol (char 9633))

(defn bar-string [value max-value]
  (let [len (* max-width (/ value max-value))]
    (str/join (repeat len bar-symbol))))



(defn table-rows [days-of-month]
  (loop [[d & days] (reverse (sort (keys days-of-month)))
         res        [:tbody]]
    (if-not d
      res
      (recur days
             (into res
                   (let [hours (reverse (sort (days-of-month d)))
                         max-val (apply max (vals hours))]
                     (mapv
                        (fn [[v v1]]
                          [:tr
                           [:td {:class "px-6 py-4"} d]
                           [:td {:class "px-6 py-4"} v]
                           [:td {:class "px-6 py-4"} v1]
                           [:td {:class "px-6 py-4"} (bar-string v1 max-val)]])
                      hours)))))))

(defn monthly-time-table [[month entries]]
  (let [m-name (-> month
                   jt/month
                   str/lower-case
                   str/capitalize)]
    [:div
     [:h3 {:class "text-l font-bold"} m-name]
     [:div {:class "relative overflow-x-auto shadow-md sm:rounded-lg"}
      [:table {:class "w-full text-sm text-left rtl:text-right text-gray-500"}
       [:thead {:class "text-xs text-gray-700 uppercase"}
        [:tr
         [:th {:scope "col" :class "px-6 py-3"} "Day"]
         [:th {:scope "col" :class "px-6 py-3"} "Hour"]
         [:th {:scope "col" :class "px-6 py-3"} "No. Domains"]
         [:th {:scope "col" :class "px-6 py-3"} ""]]]
       (table-rows entries)]]]))

(defn time-data-table [db]
  (first (mapv monthly-time-table (db 2025))))
