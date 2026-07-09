(ns user
  (:require [clj-reload.core :as reload]
            [clojure+.error :as error]
            [clojure+.print :as print]
            [clojure+.hashp :as hashp]
            [web-boilerplate.server :as server]
            [web-boilerplate.handler :as handler]
            [web-boilerplate.config :as config]
            [web-boilerplate.pathom :as pathom]
            [web-boilerplate.portal :as portal]))

(error/install!)
(print/install!)
(hashp/install!)

(reload/init {:dirs ["src" "dev"]})

(alter-var-root #'clojure.core/require
  (fn [original]
    (fn [& args]
      (when (some #{:reload :reload-all} args)
        (throw (ex-info "禁止 :reload/:reload-all，請用 (user/reset)" {})))
      (apply original args))))

(defn start []
  (server/start-server! #'handler/app))

(defn stop []
  (server/stop-server!))

(defn reset []
  (start)
  (reload/reload)
  (println "✓ Code reloaded"))

(defn restart []
  (stop)
  (reset)
  (start))

(defn portal []
  (portal/open!))

(defn cleanup []
  (println "Cleaning up resources...")
  (try
    (stop)
    (println "✓ Cleanup completed")
    (catch Exception e
      (println "✗ Cleanup error:" (.getMessage e)))))

(comment
  (start)
  (portal)
  (pathom/start-pathom!)
  (config/load-config!)
  (restart)
  (reset)
  (cleanup)
  )
