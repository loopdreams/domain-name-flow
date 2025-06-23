(ns domain-name-flow.server
  (:require [ring.middleware.defaults :as middleware]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.response :refer [response content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [clojure.core.async.flow :as flow]
            [ring.adapter.jetty9 :as jetty]
            [hiccup2.core :as h]
            [ring.websocket :as ringws]
            [ring.websocket.protocols :as ws]
            [clojure.core.async :as a :refer [thread <!!]]
            [clojure.tools.logging :as log]
            [domain-name-flow.page :as page]
            [domain-name-flow.tables :as tables]))

(defn keep-alive [socket]
  (thread
    (while (ws/-open? socket)
      (<!! (a/timeout 1000))
      (ws/-ping socket nil))))

(def conns (atom #{}))

(defn ws-handler [upgrade-request]
  {:ring.websocket/listener
   {:on-open (fn on-connect [ws]
               (log/info "connect" (:headers upgrade-request))
               (swap! conns conj ws)
               (keep-alive ws))
    :on-message (fn on-text [ws text-message]
                  (log/info "received msg:" text-message)
                  (ringws/send ws (str "echo: " text-message)))
    :on-close (fn on-close [ws status-code reason]
                (swap! conns disj ws)
                (log/info "closed" status-code reason))
    :on-pong (fn on-pong [_ _]
               (log/debug "pong"))
    :on-error (fn on-error [_ throwable]
                (.printStackTrace throwable)
                (log/error (.getMessage throwable)))}})

#_(defn broadcaster [msg]
    (pmap #(ringws/send % (str (h/html [:div {:id "notify"} (format "Average Domain Name Length: %.2f characters" msg)]))) @conns))

(defn broadcaster-name-stats [msg]
  (let [{:keys [n-items sum max min average]} msg]
    (pmap #(ringws/send % (str (h/html [:div {:id "stats"}
                                        [:ul {:class "list-disc list-inside"}
                                         [:li [:span {:class "border-solid border-1 bg-[#f1e3d3] px-1"} (format "%,2d" n-items)] " domain names received"]
                                         [:li (format "The average name length is %.2f characters" average)]
                                         [:li (format "The longest name is %d characters" max)]
                                         [:li (format "The shortest name is %d characters" min) ]]]))) @conns)))

(defn broadcaster-gtlds [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "gtlds"} msg]))) @conns))

(defn broadcaster-cctlds [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "cctlds"} msg]))) @conns))

(defn broadcaster-rate [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "rate"} msg]))) @conns))

(defn broadcaster-certs [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "certs"} msg]))) @conns))

(defn broadcaster-timestamps [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "timestamps"} msg]))) @conns))




(defn handler-main [req]
  (if (jetty/ws-upgrade-request? req)
    (ws-handler req)
    (page/main-page req)))

(compojure/defroutes app
  (compojure/GET "/" req (handler-main req)))

(defn server-start
  [& args]
  (jetty/run-jetty (-> app
                       (wrap-defaults site-defaults)
                       (wrap-resource "public"))
                   {:port 3000
                    :join? false}))

(comment
  (def server (server-start))
  (.stop server)

  (pmap  #(ringws/send % (str (h/html [:div {:id "notify"} "YYY"]))) @conns)
  (count @conns))

(defn webserver
  ([] {:ins {:name-stats   "Channel to receive stats about domain names"
             :t-stamp-rate "Channel to receive url rates"
             :frequencies  "Channel to receive name frequencies"
             :time-counts  "Channel to receive timestamp counts"}})

  ([args] (-> args (assoc :server (server-start))))

  ([state transition]
   (case transition

     ::flow/resume
     (do
       (.stop (:server state))
       (assoc state :server (server-start)))

     (::flow/pause ::flow/stop)
     (do (println state) (.stop (:server state)) state)))

  ([state in msg]
   (do
     (case in
       :name-stats   (broadcaster-name-stats (or msg {}))
       :t-stamp-rate (broadcaster-rate msg)
       :frequencies  (let [{:keys [tlds certs]} msg
                           [gtlds cctlds]       (tables/sort-g-cc-tlds tlds)]
                       (do
                         (broadcaster-gtlds gtlds)
                         (broadcaster-cctlds cctlds)
                         (-> (sort-by val certs)
                             (reverse)
                             (tables/frequencies-grid)
                             (broadcaster-certs))))
       :time-counts (-> (tables/time-data-table msg)
                        (broadcaster-timestamps)))
     [state nil])))
