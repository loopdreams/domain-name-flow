(ns domain-name-flow.page
  (:require [hiccup2.core :as h]
            [hiccup.page :as hp]))

(defn main-page-layout [req]
  [:div
   [:h1 "Hello World"]
   [:div {:hx-ext "ws"
          :ws-connect "/"}
    [:div {:id "notify"
           :hx-swap-oob "beforeend"} "Messages here?"]
    [:div {:id "rate"
           :hx-swap-oob "beforeend"} "Messages here?"]
    [:div {:id "gtlds"
           :hx-swap-oob "beforeend"} "Messages here"]
    [:div {:id "cctlds"
           :hx-swap-oob "beforeend"} "Messages here"]]])


(defn main-page [req]
  (hp/html5
      [:head
       [:script {:src "https://unpkg.com/htmx.org@2.0.4"
                 :crossorigin "anonymous"}]
       [:script {:src "https://unpkg.com/htmx-ext-ws@2.0.2"
                 :crossorigin "anonymous"}]]
    [:body (main-page-layout req)]))
