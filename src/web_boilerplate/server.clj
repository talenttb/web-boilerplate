(ns web-boilerplate.server
  (:require [org.httpkit.server :as http]
            [com.brunobonacci.mulog :as mu]
            [web-boilerplate.config :as config]
            [web-boilerplate.nrepl :as nrepl]))

(defn- try-start-portal! []
  (try
    (when-let [start (requiring-resolve 'web-boilerplate.portal/start!)]
      (start))
    (catch Exception _
      (mu/log ::portal-skip :reason "portal dep absent (non-dev runtime)"))))

(defonce ^:private server (atom nil))

(defn stop-server!
  []
  (when-let [stop-fn @server]
    (stop-fn)
    (reset! server nil)
    (mu/log ::server-stopped)))

(defn start-server!
  [handler]
  (nrepl/start!)
  (try-start-portal!)
  (if @server
    (mu/log ::server-already-running :action "skip")
    (let [cfg (config/get-config)
          host (get-in cfg [:server :host])
          port (get-in cfg [:server :port])
          stop-fn (http/run-server
                   (fn [req] (handler req))
                   {:ip host
                    :port port})]
      (reset! server stop-fn)
      (mu/log ::server-started :host host :port port)
      stop-fn)))

(defn server-running?
  "檢查 server 是否正在運行"
  []
  (some? @server))
