(ns web-boilerplate.logging
  (:require [com.brunobonacci.mulog :as mu]
            [clojure.java.io :as io]))

(defonce publishers (atom []))

(defn- init-global-context! []
  (let [get-config (requiring-resolve 'web-boilerplate.config/get-config)
        cfg (get-config)
        ctx {:env (get-in cfg [:app :env])
             :host (.getHostName (java.net.InetAddress/getLocalHost))}]
    (mu/set-global-context! ctx)))

(defn- start-console-publisher! []
  (mu/start-publisher! {:type :console
                        :pretty? true}))

(defn- start-file-publisher! [file-path]
  (let [log-file (io/file file-path)]
    (io/make-parents log-file)
    (mu/start-publisher! {:type :simple-file
                          :filename file-path})))

(defn- init-publishers! []
  (when (empty? @publishers)
    (init-global-context!)
    (let [get-config (requiring-resolve 'web-boilerplate.config/get-config)
          cfg (get-config)
          console-enabled? (get-in cfg [:logging :console :enabled] true)
          file-enabled? (get-in cfg [:logging :file :enabled] true)
          file-path (get-in cfg [:logging :file :path] "workspace/app.log")
          pubs (cond-> []
                 console-enabled? (conj (start-console-publisher!))
                 file-enabled? (conj (start-file-publisher! file-path)))]
      (reset! publishers pubs))))

(defmacro log-error!
  "記錄錯誤事件。自動添加錯誤資訊"
  [event-name error & extra-pairs]
  `(mu/log ~event-name
           :error-type (str (type ~error))
           :error-message (.getMessage ~error)
           :error-stacktrace (mapv str (.getStackTrace ~error))
           ~@extra-pairs))

(defn stop-publishers!
  "停止所有 publishers"
  []
  (doseq [pub @publishers]
    (pub))
  (reset! publishers []))

(defn restart-publishers!
  "重新初始化 publishers"
  []
  (stop-publishers!)
  (init-publishers!))

(defn before-ns-unload []
  (println "Reloading logging namespace...")
  (stop-publishers!))

(defn after-ns-reload []
  (init-publishers!)
  (println "✓ Logging reloaded"))

(init-publishers!)
