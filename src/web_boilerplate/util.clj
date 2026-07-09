(ns web-boilerplate.util
  (:require
   [cuerdas.core :as str]
   [nano-id.core :as nano-id])
  (:import
   [com.github.f4b6a3.uuid UuidCreator]))

(def ^:private alphanumeric
  "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

(defn gen-nanoid
  ([] (gen-nanoid 12))
  ([n] ((nano-id/custom alphanumeric n))))

(defn uuid7 []
  (UuidCreator/getTimeOrderedEpoch))

(defn eid [kind]
  (str/istr "~{kind}_~{(gen-nanoid)}"))


(comment
  (uuid7)
  (gen-nanoid)
  (gen-nanoid 8)
  (eid "user")
  (eid "order")
  ;;
  )
