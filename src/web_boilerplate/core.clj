(ns web-boilerplate.core
  (:require [com.brunobonacci.mulog :as mu]
            [web-boilerplate.server :as server]
            [web-boilerplate.handler :as handler]
            [web-boilerplate.logging :as log])
  (:gen-class))

(defn -main
  [& args]
  (mu/log ::app-starting :args args)
  (try
    (server/start-server! #'handler/app)

    (mu/log ::app-started)

    (.addShutdownHook (Runtime/getRuntime)
      (Thread. (fn []
                 (println "Shutting down...")
                 (try
                   (server/stop-server!)
                   (println "✓ Cleanup completed")
                   (catch Exception e
                     (println "✗ Shutdown error:" (.getMessage e)))))))

    @(promise)
    (catch Exception e
      (log/log-error! ::app-start-failed e)
      (.printStackTrace e)
      (System/exit 1))))
