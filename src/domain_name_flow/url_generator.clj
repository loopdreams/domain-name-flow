(ns domain-name-flow.url-generator
  (:require [jsonista.core :as json]
            [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]))

;; Some test functions to generate data similar to the kafka channel data

(defn url-generator
  "Periodically generates a random url string with either .com, .org. or .net"
  [out wait stop-atom]
  (loop []
    (let [tld (rand-nth [".com." ".net." ".org." ".eu" ".au"])
          domain (apply str (repeatedly (rand-nth (range 2 20)) #(rand-nth "abcdefghijklmnopqrstuvwxyz0123456789")))
          url (str domain tld)
          encoded (json/write-value-as-string {:domain url
                                               :timestamp (quot (System/currentTimeMillis) 1000)})
          put (a/>!! out encoded)]
      (when (and put (not @stop-atom))
        (^[long] Thread/sleep wait)
        (recur)))))


;; Process component:

(defn test-source
  "Source process for random urls"
  ;; Description
  ([] {:params {:wait "Time in ms to wait between generating"}
       :outs {:out "Output channel for urls"}})

  ;; Init
  ([args]
   (assoc args
          ::flow/in-ports {:urls (a/chan 100)}
          :stop (atom false)))

  ;; Transition
  ([{:keys [wait ::flow/in-ports] :as state} transition]
   (case transition
     ::flow/resume
     (let [stop-atom (atom false)]
       (future (url-generator (:urls in-ports) wait stop-atom))
       (assoc state :stop stop-atom))
     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; Transform
  ([state in msg]
   [state (when (= in :urls) {:out [msg]})]))
