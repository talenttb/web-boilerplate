(ns web-boilerplate.pathom.plugins
  (:require [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [clojure.string :as str]
            [com.brunobonacci.mulog :as mu]))

(defn view-attr? [attr]
  (let [n (name attr)]
    (and (str/starts-with? n "<")
         (str/ends-with? n ">"))))

(defn error-hiccup [attr]
  [:div.error (str (name attr) " 載入失敗")])

(p.plugin/defplugin view-error-plugin
  {::pcr/wrap-resolve
   (fn [resolve]
     (fn [env input]
       (let [node (::pcp/node env)
             expects (::pcp/expects node)]
         (if (some view-attr? (keys expects))
           (try
             (resolve env input)
             (catch Exception e
               (mu/log ::view-resolver-error
                       :op (::pco/op-name node)
                       :error (.getMessage e))
               (into {} (map (fn [attr] [attr (error-hiccup attr)]) (keys expects)))))
           (resolve env input)))))})

(p.plugin/defplugin error-logging-plugin
  {::pcr/wrap-resolve
   (fn [resolve]
     (fn [env input]
       (try
         (resolve env input)
         (catch Exception e
           (mu/log ::resolver-error
                   :op (::pco/op-name (::pcp/node env))
                   :error (.getMessage e))
           (throw e)))))})

(p.plugin/defplugin mutation-error-plugin
  {::pcr/wrap-mutate
   (fn [mutate]
     (fn [env params]
       (try
         (assoc (mutate env params) :mutation/ok? true)
         (catch Exception e
           (mu/log ::mutation-error
                   :error (.getMessage e))
           {:mutation/ok? false
            :mutation/error (.getMessage e)}))))})

(def all-plugins
  [error-logging-plugin
   view-error-plugin
   mutation-error-plugin])
