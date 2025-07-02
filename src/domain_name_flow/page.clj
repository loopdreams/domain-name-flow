(ns domain-name-flow.page
  (:require [hiccup2.core :as h]
            [hiccup.page :as hp]
            [domain-name-flow.timestamps-db :refer [ds]]
            [jsonista.core :as json]))

(defn component-headings [label]
  (case label
    "stats"        "Current Stats"
    "gtlds"        "gTLDs"
    "cctlds"       "ccTLDs"
    "hourly-count" ""
    "certs"        "Certificate Authorities"
    "logs"         "Certificate Authority Logs"))

(defn ws-component [label]
  [:div {:class "my-5"}
   [:h3 {:class "text-xl font-bold"}
    (component-headings label)]
   [:div {:id label
          :hx-swap-oob "beforeend"}
    "Waiting for messages..."]])

;; collapse functionality taken from reddit post - https://www.reddit.com/r/tailwindcss/comments/182mb9j/design_a_collapsible_and_expandable_panel_using/
;; very hacky/not ideal
(defn ws-component-collapsible [label]
  [:div {:class "my-5"}
   [:label
    [:input {:class "peer absolute scale-0" :type "checkbox"}]
    [:h3 {:class "pr-3 text-xl font-bold cursor-pointer block peer-checked:hidden"} (str  "&#9654; "(component-headings label))]
    [:h3 {:class "pr-3 text-xl font-bold cursor-pointer hidden peer-checked:block"} (str "&#9660; " (component-headings label))]
    [:span {:class "overflow-hidden transition-all duration-300 hidden peer-checked:block"}
     [:div {:id label
            :hx-swap-oob "beforeend"}
      "Waiting for messages..."]]]])

(def about-text
  (let [link-style "font-medium text-[#8458B3] hover:underline"]
    [:div {:id "about"
           :class "pt-10"}
     [:p
      "This page reads the stream of newly registered domain names that are broadcast by "
      [:a {:href "https://openintel.nl/data/zonestream/"
           :class link-style}
       "zonestream"]
      ", an open data project by "
      [:a {:href "https://openintel.nl/"
           :class link-style} "OpenIntel"]
      ". The domain names originated in Certificate Transparency logs."]
     [:br]
     [:p "The domains are then split, processed further, and grouped by things like top-level domain or the number of domains registered per hour."]
     [:br]
     [:p
      "More info and source code can be found "
      [:a {:href "https://github.com/loopdreams"
           :class link-style} "here."]]]))

;; Colours - https://www.behance.net/gallery/80191113/Minimalist-Color-Palettes-are-back#
(defn main-page-layout [req]
  [:div {:id "main" :class "max-w-2xl m-auto mt-5 p-2"}
   [:h1 {:class "font-mono text-3xl font-bold bg-gradient-to-r from-[#8458B3] via-[#D0BDf4] to-[#8458B3] inline-block text-transparent bg-clip-text"} "Domain Name Flow"]
   [:hr {:class "text-[#8458B3]"}]
   about-text
   (reduce into
           [:div {:hx-ext "ws" :ws-connect "/"}]
           [[(ws-component "stats")]
            (mapv ws-component-collapsible ["gtlds" "cctlds" "certs" "logs"])
            [(ws-component "hourly-count")]])
   [:div {:id    "echarts"
          :style "width: 670px; height: 400px;"}]])


(defn main-page [req]
  (hp/html5
      [:head
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:rel "stylesheet" :href "/css/styles.css"}]
       [:link {:rel "stylesheet" :href "/css/tw.css"}]]
    [:body {:class "bg-[#E5EAF5] text-[#494D5F]"}
     (main-page-layout req)
     [:script {:src "/js/main.js" :defer true}]
     [:script {:src "/js/libs.js"}]]))
