(ns domain-name-flow.utils
  (:require [clojure.core.async :as a]
            [clojure.string :as str]))

;; URL generator for testing

(defn url-generator
  "Periodically generates a random url string with either .com, .org. or .net"
  [out wait stop-atom]
  (loop []
    (let [tld (rand-nth [".com." ".net." ".org."])
          domain (apply str (repeatedly (rand-int 20) #(rand-nth "abcdefghijklmnopqrstuvwxyz0123456789")))
          url (str domain tld)
          put (a/>!! out url)]
      (when (and put (not @stop-atom))
        (^[long] Thread/sleep wait)
        (recur)))))

;; For TLD splitter

(defn split-url-string [^String url-string]
  (str/split url-string #"\."))

;; For domain name averager

(defn update-average [{:keys [n-items sum average] :as avgs} new-domain]
  (let [nxt-sum (+ sum (count new-domain))
        nxt-n (inc n-items)
        nxt-avg (float (/ nxt-sum nxt-n))]
    {:n-items nxt-n
     :sum nxt-sum
     :average nxt-avg}))
