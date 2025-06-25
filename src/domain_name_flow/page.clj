(ns domain-name-flow.page
  (:require [hiccup2.core :as h]
            [hiccup.page :as hp]
            [domain-name-flow.timestamps-db :refer [ds echart-spec-create]]
            [jsonista.core :as json]))


(defn component-headings [label]
  (case label
    "stats"  "Current Stats"
    "rate"   "Rate"
    "gtlds"  "gTLDs"
    "cctlds" "ccTLDs"
    "timestamps" "Domain Counts by Time"
    "certs"  "Certificate Authorities"))

(defn ws-component [label]
  [:div {:class "my-5"}
   [:h3 {:class "text-xl font-bold"}
    (component-headings label)]
   [:div {:id label
          :hx-swap-oob "beforeend"}
    "Waiting for messages..."]])

(defn main-page-layout [req]
  [:div {:id "main" :class "max-w-2xl m-auto mt-5 p-2"}
   [:h1 {:class "text-3xl font-bold text-[#e0afa0]"} "Domain Name Flow"]
   [:p {:id "about"
        :class "py-10"} "Introductory text here..."]
   (into
    [:div {:hx-ext "ws"
           :ws-connect "/"}]
    (mapv ws-component ["stats" "rate" "gtlds" "cctlds" "certs" "timestamps"]))])

(def test-vl-config
  {:$schema "https://vega.github.io/schema/vega-lite/v6.json"
   :data {:values [
                   {:a 'A', :b 28},
                   {:a 'B', :b 55},
                   {:a 'C', :b 43},
                   {:a 'D', :b 91},
                   {:a 'E', :b 81},
                   {:a 'F', :b 53},
                   {:a 'G', :b 19},
                   {:a 'H', :b 87},
                   {:a 'I', :b 52},]}
   :mark "bar"
   :encoding {:x {:field "a" :type "ordinal"}
              :y {:field "b" :type "quantitative"}}})

(def test-ec-config
  {:title {:text "Test"}
   :tooltip {}
   :legend {:data ["sales"]}
   :xAxis {:data ["Shirts" "Cardigans" "Chiffons" "Pants"]}
   :yAxis {}
   :series [{:name "sales"
             :type "bar"
             :data [5 20 36 42]}]})



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
    [:div {:id "echarts"
           :style "width: 600px;height: 400px;"}]
    [:script {:type "text/javascript"}
     (str "var myChart = echarts.init(document.getElementById('echarts'));

           myChart.setOption(" (json/write-value-as-string (echart-spec-create ds)) ");")]]))
