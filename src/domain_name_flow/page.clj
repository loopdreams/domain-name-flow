(ns domain-name-flow.page
  (:require [hiccup2.core :as h]
            [hiccup.page :as hp]
            [domain-name-flow.tables :as tables]
            [domain-name-flow.timestamps-db :refer [ds]]
            [jsonista.core :as json]
            [clojure.java.io :as io]
            [taoensso.telemere :as tel]
            [java-time.api :as jt]
            [clojure.string :as str]))

(defn component-headings [label]
  (case label
    "stats"            "Current Stats"
    "stats-historical" "Stats for the Month"
    "gtlds"            "gTLDs"
    "cctlds"           "ccTLDs"
    "hourly-count"     ""
    "certs"            "Certificate Authorities"
    "logs"             "Certificate Authority Logs"))

(defn section-component [label component-data]
  [:div {:class "my-5"}
   [:h3 {:class "text-xl font-bold"} (component-headings label)]
   component-data])

;; collapse functionality taken from reddit post - https://www.reddit.com/r/tailwindcss/comments/182mb9j/design_a_collapsible_and_expandable_panel_using/
;; very hacky/not ideal
(defn section-component-collapsible [label component-data]
  [:div {:class "my-5"}
   [:label
    [:input {:class "peer absolute scale-0" :type "checkbox"}]
    [:h3 {:class "pr-3 text-xl font-bold cursor-pointer block peer-checked:hidden"} (str "&#9654; " (component-headings label))]
    [:h3 {:class "pr-3 text-xl font-bold cursor-pointer hidden peer-checked:block"} (str "&#9660; " (component-headings label))]
    [:span {:class "overflow-hidden transition-all duration-300 hidden peer-checked:block"}
     component-data]]])

(defn ws-component [label]
  (section-component
   label
   [:div {:id label :hx-swap-oob "beforeend"}
    "Waiting for information from server..."]))

(defn ws-component-collapsible [label]
  (section-component-collapsible
   label
   [:div {:id label :hx-swap-oob "beforeend"}
    "Waiting for information from server..."]))

(defn format-stats-component [msg]
  (let [{:keys [n-items sum max min average]} msg]
    [:ul {:class "list-disc list-inside"}
     [:li [:span {:class "border-solid border-1 bg-[#D0BDF4] px-1"} (format "%,2d" n-items)] " domain names received"]
     [:li (format "The average name length is %.2f characters" average)]
     [:li (format "The longest name is %d characters" max)]
     [:li (if (= min 1)
            (format "The shortest name is %d character" min)
            (format "The shortest name is %d characters" min))]]))

(def link-style "font-medium text-[#8458B3] hover:underline")
(def main-bg-colours "bg-[#E5EAF5] text-[#494D5F]")
;; Colours - https://www.behance.net/gallery/80191113/Minimalist-Color-Palettes-are-back#

(defn about-text []
  (let [available-months (mapv str (.list (io/file "db/historical/")))]
    [:div {:id "about" :class "pt-10"}
     [:p "This page reads the real-time stream of newly registered domain names that are broadcast by "
      [:a {:href "https://openintel.nl/data/zonestream/" :class link-style} "zonestream"]
      ". Zonestream is part of an open data initiative by "
      [:a {:href "https://openintel.nl/" :class link-style} "OpenINTEL"]
      ", a joint project of the University of Twente, SIDN, NLnet Labs and SURF. The domain names originated in Certificate Transparency logs."]
     [:br]
     [:p "The domains are then split, processed further, and grouped by things like top-level domain or the number of domains registered per hour."]
     [:br]
     [:p "More info and source code can be found "
      [:a {:href "https://github.com/loopdreams/domain-name-flow" :class link-style} "here."]]
     (when (seq available-months)
       [:p "This data restarts every month. Counts for previous months are "
        [:a {:href "/fl/historical-months" :class link-style} "here."]])]))

(defn default-page-layout [& body]
  [:div {:id "main" :class "max-w-2xl m-auto mt-5 p-2"}
   [:h1 {:class "font-mono text-3xl font-bold bg-gradient-to-r from-[#8458B3] via-[#D0BDf4] to-[#8458B3] inline-block text-transparent bg-clip-text"} "Domain Name Flow"]
   [:hr {:class "text-[#8458B3]"}]
   body])

(defn main-page-layout [req]
  (default-page-layout
   (about-text)
   (reduce into
           [:div {:hx-ext "ws" :ws-connect "/fl"}]
           [[(ws-component "stats")]
            (mapv ws-component-collapsible ["gtlds" "cctlds" "certs" "logs"])
            [(ws-component "hourly-count")]])
   [:div {:id    "echarts"
          :style "width: 670px; height: 400px;"}]))

(def head-data
  [:head
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link {:rel "stylesheet" :href "/css/styles.css"}]
    [:link {:rel "stylesheet" :href "/css/tw.css"}]])

(defn id->month-name [id]
  (-> id
      (subs 4)
      (parse-long)
      (jt/month)
      str
      (str/capitalize)))

(defn historical-months-index [req]
  (let [available-months (mapv str (.list (io/file "db/historical/")))]
    (hp/html5
        head-data
        [:body {:class main-bg-colours}
         (default-page-layout
          [:h2 {:class "text-xl mt-8 mb-2 font-bold" } "Previous Months"]
          (into [:ul]
                (for [m available-months]
                  [:li [:a {:href (str "/fl/historical-months/" m)
                            :class link-style}
                        (str (subs m 0 4) ", " (id->month-name m))]]))
          [:br]
          [:p [:a {:href "/fl" :class link-style} "Home"]])])))




(defn historical-months-page [id]
  (let [[frequencies-file stats-file]  (rest (file-seq (io/as-file (str "db/historical/" id))))
        _ (tel/log! {:level :info :msg (str frequencies-file)})
        _ (tel/log! {:level :info :msg (io/as-file (str "db/historical/" id))})
        {:keys [_timestamp tlds certs]} (read-string (slurp frequencies-file))
        {:keys [_timestamp stats]}      (read-string (slurp stats-file))
        [gtlds cctlds]                 (tables/sort-g-cc-tlds tlds)
        [certs-freq logs-freq]         (tables/sort-certs-db certs)]
    (hp/html5
        head-data
      [:body {:class main-bg-colours}
       (default-page-layout
        [:h2 {:class "text-xl my-8 font-bold"}
         (str "Historical Recordings for " (id->month-name id) ", " (subs id 0 4))]
        [:p [:a {:href "/fl/historical-months" :class link-style} "Back to months index"]]
        [:p [:a {:href "/fl" :class link-style} "Home"]]
        (section-component "stats-historical" [:div (format-stats-component stats)])
        (section-component-collapsible "gtlds" gtlds)
        (section-component-collapsible "cctlds" cctlds)
        (section-component-collapsible "certs" certs-freq)
        (section-component-collapsible "logs" logs-freq))])))

(defn main-page [req]
  (hp/html5
      head-data
    [:body {:class main-bg-colours}
     (main-page-layout req)
     [:script {:src "/js/main.js" :defer true}]
     [:script {:src "/js/libs.js"}]]))
