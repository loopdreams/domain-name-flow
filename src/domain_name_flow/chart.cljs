(ns domain-name-flow.chart
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require ["echarts" :as echarts]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defn echart-spec [{:keys [series offset]}]
  {:xAxis    {:type "time"
              :name "UTC"}
   :tooltip {:trigger "axis"}
   :yAxis    {:name "Domain Counts (Hourly)"}
   :series   {:type "line"
              :data series}
   :dataZoom [{:type       "slider"
               :startValue offset
               :filterMode "none"}]})

(def myChart (. echarts (init (. js/document (getElementById "echarts")))))

(go (let [resp (<! (http/get "./timestamp-counts" {}))]
      (. myChart (setOption (clj->js (echart-spec (-> resp :body)))))))
