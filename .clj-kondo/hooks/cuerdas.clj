(ns hooks.cuerdas
  (:require [clj-kondo.hooks-api :as api]))

(defn- scan [s start]
  (let [n (count s)]
    (loop [i start depth 1 in-str? false esc? false]
      (if (>= i n)
        [n (subs s start i)]
        (let [c (nth s i)]
          (cond
            in-str? (cond
                      esc?     (recur (inc i) depth true false)
                      (= c \\) (recur (inc i) depth true true)
                      (= c \") (recur (inc i) depth false false)
                      :else    (recur (inc i) depth true false))
            (= c \")                  (recur (inc i) depth true false)
            (contains? #{\( \[ \{} c) (recur (inc i) (inc depth) false false)
            (contains? #{\) \] \}} c) (if (= 1 depth)
                                        [(inc i) (subs s start i)]
                                        (recur (inc i) (dec depth) false false))
            :else                     (recur (inc i) depth false false)))))))

(defn- forms-in [s]
  (let [n (count s)]
    (loop [i 0 out []]
      (if (>= i n)
        out
        (let [c  (nth s i)
              c2 (when (< (inc i) n) (nth s (inc i)))]
          (if (and (= c \~) (or (= c2 \{) (= c2 \()))
            (let [[end inner] (scan s (+ i 2))]
              (recur end (conj out (if (= c2 \()
                                     (str "(" inner ")")
                                     inner))))
            (recur (inc i) out)))))))

(defn istr [{:keys [node]}]
  (let [tmpl   (apply str (keep (fn [a]
                                  (let [v (api/sexpr a)]
                                    (when (string? v) v)))
                                (rest (:children node))))
        forms  (filter #(re-find #"\S" %) (forms-in tmpl))
        nodes  (map (fn [f] (api/coerce (read-string f))) forms)
        result (api/list-node (list* (api/token-node 'clojure.core/str) nodes))]
    {:node (with-meta result (meta node))}))
