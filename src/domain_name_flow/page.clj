(ns domain-name-flow.page
  (:require [hiccup2.core :as h]
            [hiccup.page :as hp]
            [domain-name-flow.timestamps-db :refer [ds echart-spec-create]]
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

(defn main-page-layout [req]
  [:div {:id "main" :class "max-w-2xl m-auto mt-5 p-2"}
   [:h1 {:class "text-3xl font-bold text-[#e0afa0]"} "Domain Name Flow"]
   [:p {:id "about"
        :class "py-10"} "Introductory text here..."]
   (reduce into
    [:div {:hx-ext "ws"
           :ws-connect "/"}]
    [[(ws-component "stats")]
     (mapv ws-component-collapsible ["gtlds" "cctlds" "certs" "logs"])
     [(ws-component "hourly-count")]])
   [:div {:id "echarts"
          :style "width: 670px; height: 400px;"}]])


(defn main-page [req]
  (hp/html5
   [:head
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:link {:rel "stylesheet"
            :href "/css/styles.css"}]
    [:link {:rel "stylesheet"
            :href "/css/tw.css"}]
    [:script {:src "https://unpkg.com/htmx.org@2.0.4"
              :crossorigin "anonymous"}]
    [:script {:src "https://unpkg.com/htmx-ext-ws@2.0.2"
              :crossorigin "anonymous"}]
    [:script {:src "https://cdn.jsdelivr.net/npm/echarts@5.6.0/dist/echarts.min.js"}]]
   [:body (main-page-layout req)
    [:script {:type "text/javascript"}
     (str "var myChart = echarts.init(document.getElementById('echarts'));

           myChart.setOption(" (json/write-value-as-string (echart-spec-create ds)) ");")]]))
