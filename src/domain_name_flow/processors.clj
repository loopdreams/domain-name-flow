(ns domain-name-flow.processors
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [clojure.core.async :as a]
            [clojure.edn :as edn]
            [taoensso.telemere :as tel]
            [clojure.java.io :as io]
            [clojure.core.async.flow :as flow]))


(defn split-url-string [^String url-string]
  (str/split url-string #"\."))

(defn parse-cert-auth-string [^String ct-name]
  (cond
    (re-find #"Argon" ct-name)     ["Google" "Argon"]
    (re-find #"Xenon" ct-name)     ["Google" "Xenon"]
    (re-find #"Nimbus" ct-name)    ["Cloudflare" "Nimbus"]
    (re-find #"Yeti" ct-name)      ["Digicert" "Yeti"]
    (re-find #"Nessie" ct-name)    ["Digicert" "Nessie"]
    (re-find #"Wyvern" ct-name)    ["Digicert" "Wyvern"]
    (re-find #"Sphinx" ct-name)    ["Digicert" "Sphinx"]
    (re-find #"Sabre" ct-name)     ["Sectigo" "Sabre"]
    (re-find #"Mammoth" ct-name)   ["Sectigo" "Mammoth"]
    (re-find #"Elephant" ct-name)  ["Sectigo" "Elephant"]
    (re-find #"Oak" ct-name)       ["Let's Encrypt" "Oak"]
    (re-find #"TrustAsia" ct-name) ["TrustAsia" "Log"]
    :else                          [ct-name "?"]))

(defn add-previous-stop-time [{:keys [backup-file] :as args}]
  (when (.exists (io/as-file backup-file))
    (let [{:keys [timestamp]} (edn/read-string (slurp backup-file))]
      (assoc args :previous-stoptime (-> timestamp (.getTime) (/ 1000) long)))))

(defn record-handler
  ([] {:ins      {:records "Channel to receive kafka records"}
       :outs     {:domains    "Channel to send extracted domain names."
                  :timestamps "Channel to foward timestamps."
                  :names      "Channel to forward tld/cert authority names."}
       :params   {:backup-file "File with system backup info. Used to get previous stop time."}
       :workload :mixed})

  ([args] (add-previous-stop-time args))

  ([state _transition] state)

  ([{:keys [previous-stoptime] :as state} _input-id msg]
   (let [{:keys [domain
                 cert_index
                 ct_name
                 timestamp]}
         (json/read-value msg json/keyword-keys-object-mapper)
         [domain tld]        (split-url-string domain)
         [c-authority c-log] (parse-cert-auth-string ct_name)]
     (if (or (not previous-stoptime)
             (> timestamp previous-stoptime)) ;; don't double-count (a bit pointless here, doesn't work like this in practice)
       [state {:domains    [domain]
               :timestamps [timestamp]
               :names      [{:cert c-authority}]
               :log        c-log
               :tld        tld}]
       [state nil]))))


;; Average domain names length


(defn update-stats [{:keys [n-items sum _average max min] :as stats} new-domain]
  (let [n-len (count new-domain)
        n-nxt (inc n-items)
        n-sum (+ sum n-len)]
    {:n-items n-nxt
     :sum     n-sum
     :min     (if (< n-len min) n-len min)
     :max     (if (> n-len max) n-len max)
     :average (float (/ n-sum n-nxt))}))


(defn domain-name-stats
  ([] {:ins    {:domains "Channel to receive domain strings"
                :push    "Channel to signal when to push to server component"
                :reset   "Channel to receive signal to reset counts"
                :backup  "Channel to receive signal to backup stats to file (overwrites file)"}
       :outs   {:name-stats "Channel to send stat values"}
       :params {:backup-file "file name of edn backup file."}})
  ([{:keys [backup-file] :as args}]
   (if (and (.exists (io/as-file backup-file))
            (seq (edn/read-string (slurp backup-file))))
     (let [{:keys [stats]} (edn/read-string (slurp backup-file))]
       (assoc args :name-stats stats))
     (assoc args :name-stats {:n-items 0
                              :sum     0
                              :min     1000
                              :max     0
                              :average 0})))
  ([state _transition] state)
  ([state id msg]
   (case id

     :domains
     [(assoc state :name-stats (update-stats (:name-stats state) msg))]

     :push
     [state (when (> (:n-items (:name-stats state)) 0)
              {:name-stats [(:name-stats state)]})]
     :backup
     (do
       (let [time      (java.util.Date.)
             save-data {:timestamp time
                        :stats     (:name-stats state)}]
         (spit (:backup-file state) save-data))
       [state nil])
     :reset
     [(assoc state :name-stats {:n-items 0
                                :sum     0
                                :min     1000
                                :max     0
                                :average 0}) nil]
     [state nil])))


(defn name-frequencies-processor
  "Takes in a name (tld/gtld/cert authority) and adds it to a frequency map.
  Pushes to server on push signal."
  ([] {:ins {:names "Channel to receive a name on."
             :push "Channel to receive push to server signal"
             :backup "Channel to receive signal to backup dbs to file. Overwrites files."}
       :outs {:frequencies "Channel to send name frequencies map"}
       :params {:backup-file "File name for backups"}
       :workload :mixed})

  ([{:keys [backup-file] :as args}]
   (if (and (.exists (io/as-file backup-file))
            (seq (edn/read-string (slurp backup-file))))
     (let [{:keys [tlds certs]} (edn/read-string (slurp backup-file))]
       (assoc args
              :tld-db (atom tlds)
              :cert-db (atom certs)))
     (assoc args
            :tld-db (atom {})
            :cert-db (atom {}))))

  ([state _transition] state)

  ([{:keys [tld-db cert-db] :as state} input-id {:keys [cert log tld]}]
   (case input-id
     :names
     (do
       (swap! tld-db update tld (fnil inc 0))
       (swap! cert-db update-in [cert log] (fnil inc 0))
       [state nil])
     :backup
     (do
       (let [tlds @tld-db
             certs @cert-db
             time (java.util.Date.)
             save-data {:timestamp time
                        :tlds tlds
                        :certs certs}]
         (spit (:backup-file state) save-data))
       [state nil])
     :push
     [state (when (and (seq @tld-db) (seq @cert-db))
              {:frequencies [{:tlds @tld-db
                              :certs @cert-db}]})])))

;; Scheduler - schedule push to websocket (down the line)

(defn scheduler
  ([] {:outs {:push "Channel to send push signal"}
       :params {:wait "Scheduler frequency"}})

  ([args] (assoc args
                 ::flow/in-ports {:alarm (a/chan 10)}
                 :stop (atom false)))

  ([{:keys [wait ::flow/in-ports] :as state} transition]
   (case transition

     ::flow/resume
     (let [stop-atom (atom false)]
       (future (loop []
                 (let [put (a/>!! (:alarm in-ports) true)]
                   (when (and put (not @stop-atom))
                     (^ [long] Thread/sleep wait)
                     (recur)))))
       (assoc state :stop stop-atom))

     (::flow/pause ::flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ([state in _msg]
   [state (when (= in :alarm) {:push [true]})]))
