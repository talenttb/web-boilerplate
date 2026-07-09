(ns web-boilerplate.portal
  (:require [portal.api :as p]))

(defonce portal-instance (atom nil))

(defn start!
  []
  (when-not @portal-instance
    (let [get-config (requiring-resolve 'web-boilerplate.config/get-config)
          cfg (get-config)
          port (get-in cfg [:portal :port])
          options (cond-> {:launcher false}
                    port (assoc :port port))
          instance (p/open options)]
      (reset! portal-instance instance)
      (add-tap #'p/submit))))

(defn open!
  []
  (if @portal-instance
    (let [get-config (requiring-resolve 'web-boilerplate.config/get-config)
          cfg (get-config)
          launcher (get-in cfg [:portal :launcher])]
      (if launcher
        (p/open {:launcher launcher})
        (p/open))
      (println (str "✓ Portal UI opened" (when launcher (str " in " (name launcher))))))
    (println "✗ Portal not started. Call 'start!' first.")))

(defn stop!
  []
  (when @portal-instance
    (remove-tap #'p/submit)
    (try
      (p/close @portal-instance)
      (catch Exception _))
    (reset! portal-instance nil)))

(comment
  (start!)
  (open!)
  ;;
  )
