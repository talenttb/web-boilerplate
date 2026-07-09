(ns web-boilerplate.handler
  (:require [reitit.ring :as ring]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [clojure.edn :as edn]
            [jsonista.core :as json]
            [com.brunobonacci.mulog :as mu]
            [dev.onionpancakes.chassis.core :as chassis]
            [web-boilerplate.logging :as log]
            [web-boilerplate.pathom :as pathom]
            [web-boilerplate.demo :as demo]))

(defn json-response
  ([data] (json-response 200 data))
  ([status data]
   {:status status
    :headers {"Content-Type" "application/json; charset=utf-8"}
    :body (json/write-value-as-string data)}))

(def app-head
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
   [:title "web-boilerplate"]
   [:link {:rel "icon" :href "data:,"}]
   [:link {:rel "stylesheet" :href "/css/app.css"}]
   [:script {:type "module"
             :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.7/bundles/datastar.js"}]])

(defn render-page [content]
  (chassis/html
    [:html {:lang "zh-Hant" :data-theme "light"}
     app-head
     [:body content]]))

(defn home-handler [{:keys [request-method] :as _req}]
  (if (= :get request-method)
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (render-page
             [:main {:id "main"}
              [:h1 "web-boilerplate"]
              [:p "Clojure server-driven web 樣板：Pathom3 + Datastar + append-only DB"]
              [:ul
               [:li [:a {:href "/demo"} "旅費分帳 demo"]]
               [:li [:a {:href "/api/health"} "API Health Check"]]
               [:li [:a {:href "/api/status"} "API Status"]]]])}
    (json-response 405 {:error "Method not allowed"
                        :allowed-methods ["GET"]})))

(defn health-check [_request]
  (let [pathom-check (pathom/ping)
        all-ok? (= :ok (:status pathom-check))]
    (json-response (if all-ok? 200 503)
                   {:status (if all-ok? "ok" "degraded")
                    :timestamp (System/currentTimeMillis)
                    :checks {:pathom pathom-check}})))

(defn status [_request]
  (json-response {:app "web-boilerplate"
                  :version "0.1.0"
                  :uptime (.. java.lang.management.ManagementFactory
                              getRuntimeMXBean
                              getUptime)}))

(defn health-handler [{:keys [request-method] :as request}]
  (case request-method
    :get (health-check request)
    (json-response 405 {:error "Method not allowed"
                        :allowed-methods ["GET"]})))

(defn status-handler [{:keys [request-method] :as request}]
  (case request-method
    :get (status request)
    (json-response 405 {:error "Method not allowed"
                        :allowed-methods ["GET"]})))

(def ^:private eql-json-mapper
  (json/object-mapper
    {:encode-key-fn (fn [k] (if (keyword? k) (subs (str k) 1) (str k)))}))

(defn eql-handler [req]
  (if (not= :post (:request-method req))
    {:status 405 :body "Method not allowed"}
    (try
      (let [body-str (when-let [b (:body req)] (slurp b))
            {:keys [action query]} (when (seq body-str) (edn/read-string body-str))]
        (cond
          (nil? action)
          {:status 400
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/write-value-as-string {:error "missing :action"} eql-json-mapper)}

          (not (contains? #{:query :mutation} action))
          {:status 400
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/write-value-as-string {:error "invalid :action (must be :query or :mutation)"} eql-json-mapper)}

          (nil? query)
          {:status 400
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/write-value-as-string {:error "missing :query"} eql-json-mapper)}

          :else
          {:status 200
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/write-value-as-string (pathom/process-eql query) eql-json-mapper)}))
      (catch Exception e
        {:status 400
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body (json/write-value-as-string {:error (.getMessage e)} eql-json-mapper)}))))

(defn wrap-errors
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/log-error! ::handler-error e
                        :uri (:uri request)
                        :method (:request-method request))
        (json-response 500 {:error (.getMessage e)
                            :type (str (type e))})))))

(defn wrap-request-logging
  [handler]
  (fn [request]
    (let [{:keys [request-method uri query-string headers remote-addr]} request
          user-agent (get headers "user-agent")
          referer (get headers "referer")]
      (mu/with-context {:method request-method
                        :uri uri
                        :query-string query-string
                        :user-agent user-agent
                        :referer referer
                        :remote-addr remote-addr}
        (mu/trace ::http-request
          []
          (handler request))))))

(def routes
  [["/" {:handler #'home-handler}]
   ["/demo" {:handler #'demo/split-bill-handler}]
   ["/api"
    ["/health" {:handler #'health-handler}]
    ["/status" {:handler #'status-handler}]
    ["/eql" {:handler #'eql-handler}]]])

(def app
  (-> (ring/ring-handler
       (ring/router routes)
       (ring/create-default-handler))
      wrap-errors
      wrap-request-logging
      wrap-keyword-params
      wrap-params
      (wrap-resource "public")
      wrap-content-type))

(defn before-ns-unload []
  (println "Reloading handler namespace...")
  ((requiring-resolve 'web-boilerplate.server/stop-server!)))

(defn after-ns-reload []
  ((requiring-resolve 'web-boilerplate.server/start-server!) #'app)
  (println "✓ Handler reloaded"))
