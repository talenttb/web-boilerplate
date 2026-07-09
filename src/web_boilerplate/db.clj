(ns web-boilerplate.db
  (:require
   [cuerdas.core :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.prepare :as prepare]
   [honey.sql :as hsql]
   [jsonista.core :as json]
   [malli.core :as m]
   [malli.error :as me]
   [web-boilerplate.config :as config]
   [web-boilerplate.util :as util])
  (:import
   [com.zaxxer.hikari HikariDataSource]
   [java.util.concurrent CountDownLatch]
   [org.postgresql.util PGobject]))

(defonce datasource (atom nil))
(defonce write-latch (atom nil))
(defonce ^:private eid-locks (atom {}))

(def ^:private migration-ddls
  ["CREATE TABLE IF NOT EXISTS commits (
      id    UUID PRIMARY KEY,
      eid   TEXT NOT NULL,
      data  JSONB NOT NULL,
      t     TIMESTAMPTZ NOT NULL DEFAULT now()
    )"
   "CREATE INDEX IF NOT EXISTS commits_eid_t ON commits (eid, t DESC)"
   "CREATE INDEX IF NOT EXISTS commits_t ON commits (t)"
   "CREATE INDEX IF NOT EXISTS commits_kind ON commits ((data->>'kind'))"

   "CREATE TABLE IF NOT EXISTS refs (
      eid        TEXT PRIMARY KEY,
      commit_id  UUID NOT NULL,
      data       JSONB NOT NULL,
      t          TIMESTAMPTZ NOT NULL
    )"
   "CREATE INDEX IF NOT EXISTS refs_kind_active
      ON refs ((data->>'kind'))
      WHERE data->>'deleted_at' IS NULL"])

(defn- ->pgobject [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/write-value-as-string value))))

(defn- <-pgobject [^PGobject obj]
  (when-let [v (.getValue obj)]
    (json/read-value v json/keyword-keys-object-mapper)))

(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^java.sql.PreparedStatement s i]
    (.setObject s i (->pgobject m))))

(extend-protocol rs/ReadableColumn
  PGobject
  (read-column-by-label [^PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^PGobject v _2 _3]
    (<-pgobject v)))

(def EntityWrite
  [:and
   [:map
    [:eid [:string {:min 1}]]
    [:data [:map
            [:kind [:string {:min 1}]]]]]
   [:fn {:error/message "eid prefix 與 data.kind 不一致"}
    (fn [{:keys [eid data]}]
      (str/starts-with? eid (str (:kind data) "_")))]])

(def entity-write-validator (m/validator EntityWrite))

(defn ensure-entity! [{:keys [eid data] :as entity}]
  (when-not (entity-write-validator entity)
    (let [explained (m/explain EntityWrite entity)
          humanized (me/humanize explained)]
      (throw (ex-info (str "Entity validation failed: " humanized)
                      {:type :validation-error
                       :eid eid
                       :humanized humanized
                       :explained explained})))))

(defn- run-migration! [ds]
  (doseq [ddl migration-ddls]
    (jdbc/execute! ds [ddl])))

(defn create-datasource! []
  (let [{:keys [host port name user password pool-size sslmode]} (-> (config/get-config) :database)
        ssl-param (if sslmode (str "&sslmode=" sslmode) "")
        jdbc-url (format "jdbc:postgresql://%s:%d/%s?prepareThreshold=0%s" host port name ssl-param)
        ds (doto (HikariDataSource.)
             (.setJdbcUrl jdbc-url)
             (.setUsername user)
             (.setPassword password)
             (.setMaximumPoolSize (or pool-size 3)))]
    (reset! datasource ds)
    (run-migration! ds)
    ds))

(defn get-datasource []
  (or @datasource (create-datasource!)))

(defn ping [ds]
  (try
    (jdbc/execute-one! ds ["SELECT 1"])
    {:status :ok}
    (catch Exception e
      {:status :error :message (.getMessage e)})))

(defn stop-datasource! []
  (when-let [ds @datasource]
    (.close ds)
    (reset! datasource nil)))

(defn exec-ddl! [ds sql]
  (jdbc/execute! ds [sql]))

(def IndexSpec
  [:or
   :string
   [:map
    [:table       [:or :keyword :string]]
    [:name        [:or :keyword :string]]
    [:cols        :string]
    [:where       {:optional true} :string]
    [:unique?     {:optional true} :boolean]
    [:concurrent? {:optional true} :boolean]
    [:using       {:optional true} [:enum :btree :gin :gist :brin :hash]]
    [:include     {:optional true} [:sequential [:or :keyword :string]]]]])

(def ^:private index-spec-validator (m/validator IndexSpec))

(defn- spec->sql [{:keys [table name cols where unique? concurrent? using include]}]
  (str "CREATE " (when unique? "UNIQUE ")
       "INDEX " (when concurrent? "CONCURRENTLY ")
       "IF NOT EXISTS " (clojure.core/name name)
       " ON " (clojure.core/name table)
       (when using (str " USING " (clojure.core/name using)))
       " (" cols ")"
       (when (seq include)
         (str " INCLUDE (" (str/join ", " (map clojure.core/name include)) ")"))
       (when where (str " WHERE " where))))

(defn ensure-index! [ds spec]
  (when-not (index-spec-validator spec)
    (throw (ex-info "Invalid index spec"
                    {:type :validation-error
                     :humanized (me/humanize (m/explain IndexSpec spec))})))
  (exec-ddl! ds (if (string? spec) spec (spec->sql spec))))

(comment
  (ensure-index! (get-datasource) {:table :commits :name :commits_eid_t :cols "eid, t DESC"})

  (ensure-index! (get-datasource) {:table :commits :name :commits_kind :cols "(data->>'kind')"})

  (ensure-index! (get-datasource) {:table :refs :name :refs_active
                                   :cols "(data->>'kind')"
                                   :where "data->>'deleted_at' IS NULL"})

  (ensure-index! (get-datasource) {:table :refs :name :refs_eid_unique :cols "eid" :unique? true})

  (ensure-index! (get-datasource) {:table :commits :name :commits_gin :cols "data" :using :gin})

  (ensure-index! (get-datasource) {:table :commits :name :commits_gin_path
                                   :cols "data jsonb_path_ops"
                                   :using :gin})

  (ensure-index! (get-datasource) {:table :commits :name :commits_cover
                                   :cols "eid"
                                   :concurrent? true
                                   :include [:data :t]})

  (ensure-index! (get-datasource) "CREATE INDEX IF NOT EXISTS commits_special
                                     ON commits (eid) WITH (fillfactor = 70)")
  ;;
  )

(defn prefix-keys [prefix m]
  (persistent!
    (reduce-kv (fn [acc k v] (assoc! acc (keyword prefix (name k)) v))
               (transient {})
               m)))

(defn apply-query-opts [sql-map opts]
  (let [{:keys [select limit offset order-by]} opts]
    (cond-> sql-map
      select   (assoc :select select)
      order-by (assoc :order-by order-by)
      limit    (assoc :limit limit)
      offset   (assoc :offset offset))))

(defn pause-writes! []
  (reset! write-latch (CountDownLatch. 1)))

(defn resume-writes! []
  (when-let [latch @write-latch]
    (.countDown latch))
  (reset! write-latch nil))

(defn- await-write-permission []
  (when-let [latch @write-latch]
    (.await latch)))

(def ^:private jdbc-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn eid->kind [eid]
  (first (str/split eid #"_")))

(defn- q! [ds sql-map]
  (jdbc/execute! ds (hsql/format sql-map) jdbc-opts))

(defn- q1! [ds sql-map]
  (jdbc/execute-one! ds (hsql/format sql-map) jdbc-opts))

(defn get-ref
  ([ds eid]      (get-ref ds eid nil))
  ([ds eid opts] (q1! ds (apply-query-opts {:select [:*]
                                            :from [:refs]
                                            :where [:= :eid eid]}
                                           opts))))

(defn get-ref-by-kind [ds kind]
  (q! ds {:select [:*]
          :from [:refs]
          :where [:and
                  [:= [:raw "data->>'kind'"] kind]
                  [:raw "data->>'deleted_at' IS NULL"]]}))

(defn get-commits
  ([ds eid]      (get-commits ds eid nil))
  ([ds eid opts] (q! ds (apply-query-opts {:select [:*]
                                           :from [:commits]
                                           :where [:= :eid eid]
                                           :order-by [[:t :desc]]}
                                          opts))))

(defn get-commit-at [ds eid timestamp]
  (q1! ds {:select [:*]
           :from [:commits]
           :where [:and
                   [:= :eid eid]
                   [:<= :t timestamp]]
           :order-by [[:t :desc]]
           :limit 1}))

(defn- lock-for-eid [eid]
  (or (@eid-locks eid)
      (get (swap! eid-locks
                  (fn [m] (cond-> m (not (m eid)) (assoc eid (Object.)))))
           eid)))

(defn write! [ds eid f]
  (await-write-permission)
  #_{:clj-kondo/ignore [:locking-suspicious-lock]}
  (locking (lock-for-eid eid)
    (jdbc/with-transaction [tx ds]
      (let [current (q1! tx {:select [:*]
                             :from [:refs]
                             :where [:= :eid eid]})
            current-data (some-> current :data)
            new-data (f (or current-data {}))
            _ (ensure-entity! {:eid eid :data new-data})
            new-commit-id (util/uuid7)
            do-update-set {:fields [:commit_id :data :t]
                           :where [:= :refs/commit_id
                                   (or (:commit-id current) [:inline nil])]}
            stmt {:with [[:nc {:insert-into :commits
                               :values [{:id new-commit-id
                                         :eid eid
                                         :data [:lift new-data]
                                         :t [:now]}]
                               :returning :*}]]
                  :insert-into :refs
                  :values [{:eid eid
                            :commit_id new-commit-id
                            :data [:lift new-data]
                            :t [:now]}]
                  :on-conflict :eid
                  :do-update-set do-update-set
                  :returning :*}
            result (q1! tx stmt)]
        (when (nil? result)
          (throw (ex-info "Concurrent modification"
                          {:type :concurrent-modification :eid eid})))
        {:eid eid
         :commit-id new-commit-id
         :data new-data}))))

(defn merge! [ds eid patch]
  (write! ds eid #(merge % patch)))

(defn delete! [ds eid]
  (write! ds eid #(assoc % :deleted_at (str (java.time.Instant/now)))))

(defn before-ns-unload []
  (println "Reloading db namespace...")
  (stop-datasource!))

(defn after-ns-reload []
  (create-datasource!)
  (println "✓ Database reloaded"))

(comment
  (create-datasource!)

  (util/eid "user")
  (util/uuid7)

  (write! (get-datasource) (util/eid "user")
          (constantly {:kind "user" :name "Alice"}))

  (get-ref-by-kind (get-datasource) "user")

  (let [ds  (get-datasource)
        eid (util/eid "order")]
    (write! ds eid (constantly {:kind "order" :amount 100}))
    (write! ds eid #(assoc % :amount 200))
    (get-commits ds eid))

  (stop-datasource!)
  ;;
  )
