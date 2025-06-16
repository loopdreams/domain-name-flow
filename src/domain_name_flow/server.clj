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
            [domain-name-flow.page :as page]))

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
    (pmap #(ringws/send % (str (h/html [:div {:id "notify"}
                                        [:ul
                                         [:li (format "%d domain names received" n-items)]
                                         [:li (format "The average name length is %.2f" average)]
                                         [:li (format "The max name length is %d" max)]
                                         [:li (format "The min name length is %d" min) ]]]))) @conns)))

(defn broadcaster-gtlds [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "gtlds"} msg]))) @conns))

(defn broadcaster-cctlds [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "cctlds"} msg]))) @conns))

(defn broadcaster-rate [msg]
  (pmap #(ringws/send % (str (h/html [:div {:id "rate"} msg]))) @conns))


(defn handler-main [req]
  (if (jetty/ws-upgrade-request? req)
    (ws-handler req)
    (page/main-page req)))

(compojure/defroutes app
  (compojure/GET "/" req (handler-main req)))

(defn server-start
  [& args]
  (jetty/run-jetty (-> app
                       (wrap-defaults site-defaults))
                   {:port 3000
                    :join? false}))

(comment
  (def server (server-start))
  (.stop server)

  (pmap  #(ringws/send % (str (h/html [:div {:id "notify"} "YYY"]))) @conns)
  (count @conns))

(defn webserver
  ([] {:ins {:name-stats      "Channel to receive stats about domain names"
             :g-tld-frequencies "Channel to receive tld frequencies"
             :cc-tld-frequencies "Channel to receive tld frequencies"
             :t-stamp-rate    "Channel to receive url rates"}})

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
       :name-stats         (broadcaster-name-stats (or msg {}))
       :g-tld-frequencies  (broadcaster-gtlds msg)
       :cc-tld-frequencies (broadcaster-cctlds msg)
       :t-stamp-rate       (broadcaster-rate msg))
     [state nil])))
