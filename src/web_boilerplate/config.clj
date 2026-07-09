(ns web-boilerplate.config
  (:require
   [clojure.java.io :as io]
   [cprop.core :as cprop]
   [malli.core :as m]
   [malli.error :as me]))

(def AppConfig
  [:map
   [:env [:enum :dev :staging :prod]]
   [:node-name :string]])

(defonce config (atom nil))

(defn- validate-app-config! [app-config]
  (when-not (m/validate AppConfig app-config)
    (let [explained (m/explain AppConfig app-config)
          humanized (me/humanize explained)]
      (throw (ex-info (str "Invalid app config: " humanized)
                      {:humanized humanized :explained explained})))))

(defn load-config! []
  (let [secret-file (io/file "workspace/secret.edn")
        base-config (if (.exists secret-file)
                      (cprop/load-config :resource "config.edn"
                                         :file "workspace/secret.edn")
                      (cprop/load-config :resource "config.edn"))
        extra-config-path (System/getenv "EXTRA_CONFIG_PATH")
        final-config (if (and extra-config-path
                             (.exists (io/file extra-config-path)))
                       (do
                         (println "Loading extra config from:" extra-config-path)
                         (merge base-config (cprop/load-config :file extra-config-path)))
                       base-config)]
    (validate-app-config! (:app final-config))
    (reset! config final-config)
    @config))

(defn get-config []
  @config)

(defn before-ns-unload []
  (println "Reloading configuration..."))

(defn after-ns-reload []
  (load-config!)
  (println "✓ Configuration reloaded"))

(when-not @config
  (load-config!))
