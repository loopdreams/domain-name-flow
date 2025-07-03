(ns domain-name-flow.server
  (:require [compojure.core :as compojure]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.resource :refer [wrap-resource]]
            [clojure.core.async.flow :as flow]
            [ring.adapter.jetty9 :as jetty]
            [hiccup2.core :as h]
            [ring.websocket :as ringws]
            [ring.websocket.protocols :as ws]
            [clojure.core.async :as a :refer [thread <!!]]
            [taoensso.telemere :as tel]
            [domain-name-flow.page :as page]
            [domain-name-flow.tables :as tables]
            [domain-name-flow.timestamps-db :as db]
            [jsonista.core :as json]))

(defn keep-alive [socket]
  (thread
    (while (ws/-open? socket)
      (<!! (a/timeout 1000))
      (ws/-ping socket nil))))

(def conns (atom #{}))

(defn ws-handler [upgrade-request]
  {:ring.websocket/listener
   {:on-open    (fn on-connect [ws]
                  (tel/log! {:level :info :msg "ws-connect"})
                  (swap! conns conj ws)
                  (keep-alive ws))
    :on-message (fn on-text [ws text-message]
                  (ringws/send ws (str "echo: " text-message)))
    :on-close   (fn on-close [ws status-code reason]
                  (swap! conns disj ws))
    :on-pong    (fn on-pong [_ _])
    :on-error   (fn on-error [_ throwable]
                  (tel/log! {:level :warn :msg (.getMessage throwable)}))}})




(defn format-stats-component [msg]
  (let [{:keys [n-items sum max min average]} msg]
    [:ul {:class "list-disc list-inside"}
     [:li [:span {:class "border-solid border-1 bg-[#D0BDF4] px-1"} (format "%,2d" n-items)] " domain names received"]
     [:li (format "The average name length is %.2f characters" average)]
     [:li (format "The longest name is %d characters" max)]
     [:li (if (= min 1)
            (format "The shortest name is %d character" min)
            (format "The shortest name is %d characters" min))]]))

(defn format-hourly-count-component [msg]
  (format "%,2d domains received this hour." msg))

(defn ws-broadcaster [msg label]
  (let [b-cast (fn [label hic conns]
                 (pmap #(ringws/send % (str (h/html [:div {:id label} hic]))) conns))]
    (case label
      "stats"                           (b-cast label (format-stats-component msg) @conns)
      "hourly-count"                    (b-cast label (format-hourly-count-component msg) @conns)
      ("gtlds" "cctlds" "certs" "logs") (b-cast label msg @conns))))


(defn handler-main [req]
  (if (jetty/ws-upgrade-request? req)
    (ws-handler req)
    (page/main-page req)))

(defn get-timestamp-counts [_req]
  (let [data (db/timestamp-counts-tuples)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-value-as-string data)}))

(compojure/defroutes app
  (compojure/GET "/" req (handler-main req))
  (compojure/GET "/timestamp-counts" req (get-timestamp-counts req)))

(defn server-start
  [args]
  (jetty/run-jetty (-> app
                       (wrap-defaults site-defaults)
                       (wrap-resource "public"))
                   {:port (:port args)
                    :join? false}))

(comment
  (def server (server-start {:port 3000}))
  (.stop server))


(defn webserver
  ([] {:ins {:name-stats   "Channel to receive stats about domain names"
             :frequencies  "Channel to receive name frequencies"
             :hourly-count "Channel to receive hourly timestamp counts"}
       :params {:port "Port for server"}})

  ([args]
   (let [port (or (:port args) 3000)]
     (tel/log! {:level :info :msg (str "Server starting on port " port)})
     (-> args (assoc :server (server-start {:port port})))))

  ([{:keys [port] :as state} transition]
   (case transition

     ::flow/resume
     (do
       (.stop (:server state))
       (assoc state :server (server-start {:port port})))

     (::flow/pause ::flow/stop)
     (do (tel/log! {:level :info :msg "Stopping server"}) (.stop (:server state)) state)))

  ([state in msg]
   (do

     (case in
       :name-stats   (ws-broadcaster msg "stats")
       :frequencies  (let [{:keys [tlds certs]} msg
                           [gtlds cctlds]       (tables/sort-g-cc-tlds tlds)
                           [certs-freq logs-freq] (tables/sort-certs-db certs)]
                       (do
                         (ws-broadcaster gtlds "gtlds")
                         (ws-broadcaster cctlds "cctlds")
                         (ws-broadcaster certs-freq "certs")
                         (ws-broadcaster logs-freq "logs")))
       :hourly-count (ws-broadcaster msg "hourly-count"))
     [state nil])))
