(ns domain-name-flow.page
  (:require [hiccup2.core :as h]
            [hiccup.page :as hp]))


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
                 :crossorigin "anonymous"}]]
    [:body (main-page-layout req)]))
