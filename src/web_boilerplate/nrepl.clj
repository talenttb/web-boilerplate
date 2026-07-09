(ns web-boilerplate.nrepl
  (:require
   [web-boilerplate.config :as config]))

(defonce server (atom nil))

(defn start! []
  (when (nil? @server)
    (let [port (get-in (config/get-config) [:nrepl :port] 7888)]
      (try
        (require 'nrepl.server)
        (let [start-server (resolve 'nrepl.server/start-server)
              s (start-server :port port)]
          (reset! server s)
          (println "nREPL server started on port" port)
          s)
        (catch Exception e
          (println "Failed to start nREPL server:" (.getMessage e))
          nil)))))

(defn stop! []
  (when-let [s @server]
    (try
      (require 'nrepl.server)
      (let [stop-server (resolve 'nrepl.server/stop-server)]
        (stop-server s)
        (reset! server nil)
        (println "nREPL server stopped")
        true)
      (catch Exception e
        (println "Failed to stop nREPL server:" (.getMessage e))
        nil))))
