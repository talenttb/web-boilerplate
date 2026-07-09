(ns web-boilerplate.core
  (:require [com.brunobonacci.mulog :as mu]
            [web-boilerplate.server :as server]
            [web-boilerplate.handler :as handler]
            [web-boilerplate.logging :as log]
            [web-boilerplate.demo :as demo]
            [web-boilerplate.pathom :as pathom])
  (:gen-class))

(def pathom-resources {:demo/state demo/state})

(comment
  (require '[web-boilerplate.db :as db])
  (pathom/start-pathom! (assoc pathom-resources :db/ds (db/get-datasource))))

(defn -main
  [& args]
  (mu/log ::app-starting :args args)
  (try
    (pathom/start-pathom! pathom-resources)
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
