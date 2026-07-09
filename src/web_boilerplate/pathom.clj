(ns web-boilerplate.pathom
  (:require [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.error :as p.error]
            [com.brunobonacci.mulog :as mu]
            [web-boilerplate.config :as config]
            [web-boilerplate.resolvers.demo :as demo-resolvers]
            [web-boilerplate.pathom.plugins :as plugins]))

(defonce registry (atom nil))
(defonce plan-cache* (atom {}))

(defn start-pathom! [resources]
  (let [{:keys [parallel? lenient-mode?]
         :or   {parallel? true lenient-mode? true}} (get (config/get-config) :pathom)]
    (reset! registry
      (-> (merge {::p.a.eql/parallel? parallel?
                  ::p.error/lenient-mode? lenient-mode?}
                 resources)
          (pci/register demo-resolvers/all-resolvers)
          (pcp/with-plan-cache plan-cache*)
          (p.plugin/register plugins/all-plugins))))
  (println "✓ Pathom registry initialized (parallel mode)"))

(defn stop-pathom! []
  (reset! registry nil)
  (reset! plan-cache* {})
  (println "✓ Pathom registry cleared"))

(defn ping []
  (when-not @registry
    (throw (ex-info "Pathom 尚未注入資源：由入口（core/-main 或 user/start）呼叫 (start-pathom! core/pathom-resources)" {})))
  {:status :ok})

(defn- get-debug-config []
  (get-in (config/get-config) [:pathom :debug]))

(defn- log-slow-query! [query duration-ms stats]
  (let [cfg (get-debug-config)
        threshold (get cfg :slow-query-threshold-ms 100)]
    (when (and (get cfg :log-slow-queries)
               (> duration-ms threshold))
      (mu/log ::slow-query
              :query query
              :duration-ms duration-ms
              :threshold-ms threshold
              :resolver-count (count (::pcr/node-resolver-output stats))))))

(defn- log-query-error! [query error stats]
  (when (get-in (get-debug-config) [:log-errors])
    (mu/log ::query-error
            :query query
            :error-message (.getMessage error)
            :error-type (type error)
            :unreachable-paths (::pcr/unreachable-paths stats)
            :unreachable-resolvers (::pcr/unreachable-resolvers stats))))

(defn- log-full-stats! [query stats duration-ms]
  (when (get-in (get-debug-config) [:full-stats])
    (mu/log ::query-stats
            :query query
            :duration-ms duration-ms
            :stats stats)))

(defn process-eql
  ([query] (process-eql query nil))
  ([query entity]
   (when-not @registry
     (throw (ex-info "Pathom 尚未注入資源：由入口（core/-main 或 user/start）呼叫 (start-pathom! core/pathom-resources)" {})))
   (let [reg @registry
         debug-enabled? (get-in (get-debug-config) [:enabled])
         start-time (System/currentTimeMillis)
         result-promise (if entity
                          (p.a.eql/process reg entity query)
                          (p.a.eql/process reg query))]
     (try
       (let [result @result-promise
             duration-ms (- (System/currentTimeMillis) start-time)]
         (when debug-enabled?
           (let [stats (-> result meta ::pcr/run-stats)]
             (log-full-stats! query stats duration-ms)
             (log-slow-query! query duration-ms stats)))
         result)
       (catch Exception e
         (when debug-enabled?
           (let [_duration-ms (- (System/currentTimeMillis) start-time)
                 stats (try (-> @result-promise meta ::pcr/run-stats)
                            (catch Exception _ nil))]
             (log-query-error! query e stats)))
         (throw e))))))

(defn process-eql-with-stats
  "執行查詢並回傳結果和詳細統計（用於 REPL debugging）"
  [query]
  (if-let [reg @registry]
    (let [start-time (System/currentTimeMillis)
          result @(p.a.eql/process reg query)
          duration-ms (- (System/currentTimeMillis) start-time)
          stats (-> result meta ::pcr/run-stats)]
      {:result result
       :duration-ms duration-ms
       :stats stats
       :resolver-count (count (::pcr/node-resolver-output stats))
       :unreachable-paths (::pcr/unreachable-paths stats)})
    (throw (ex-info "Pathom registry not initialized" {}))))

(defn before-ns-unload []
  (println "Reloading pathom namespace...")
  (stop-pathom!))

(defn after-ns-reload []
  (println "Pathom 等待入口注入（user/reset 尾端的 start 會處理）"))

(comment
  (pci/register [demo-resolvers/all-resolvers
                 demo-resolvers/db-example-resolvers]))
