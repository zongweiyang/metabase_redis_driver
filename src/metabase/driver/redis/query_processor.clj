(ns metabase.driver.redis.query-processor
  (:refer-clojure :exclude [==])
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [json-path :as json-path]))

(declare compile-expression compile-function)

(defn json-path
  [query body]
  (json-path/at-path query body))

(defn compile-function
  [[operator & arguments]]
  (case (keyword operator)
    :count count
    :sum   #(reduce + (map (compile-expression (first arguments)) %))
    :float #(Float/parseFloat ((compile-expression (first arguments)) %))
    (throw (Exception. (str "Unknown operator: " operator)))))

(defn compile-expression
  [expr]
  (log/info "compile-expression" expr)
  (cond
    (keyword? expr) (partial json-path (str "$." (str (symbol expr))))
    (string? expr)  (partial json-path expr)
    (number? expr)  (constantly expr)
    (vector? expr)  (compile-function expr)
    :else           (throw (Exception. (str "Unknown expression: " expr)))))

(defn aggregate
  [rows metrics breakouts]
  (let [breakouts-fns (map compile-expression breakouts)
        breakout-fn   (fn [row] (for [breakout breakouts-fns] (breakout row)))
        metrics-fns   (map compile-expression metrics)]
    (log/info breakout-fn)
    (for [[breakout-key breakout-rows] (group-by breakout-fn rows)]
      (concat breakout-key (for [metrics-fn metrics-fns]
                             (metrics-fn breakout-rows))))))

(defn extract-fields
  [rows fields]
  (let [fields-fns (map compile-expression fields)]
    (for [row rows]
      (for [field-fn fields-fns]
        (field-fn row)))))

(defn add-column-metadata
  [fields]
  (for [field fields]
    {:name (if (keyword? field) (name field) (str field))
     :display_name (if (keyword? field) (name field) (str field))}))

(defn body->rows [body]
  (vec (flatten (conj [] body))))
(defn to-map
  [result]
  {:result (into [] (for [x result]
                      {:value x}
                      ))})

(defn execute-redis-request-reducible [origin-result respond query]
  (log/info "tomap: " (to-map origin-result))
  (let [result        (to-map origin-result)
        rows-path     (or (:path (:result query)) "$")
        rows          (body->rows (json-path rows-path result))
        fields        (or (:fields (:result query)) (reduce-kv (fn [m k _] (merge m k)) [] (first rows)))
        aggregations  (or (:aggregation (:result query)) [])
        breakouts     (or (:breakout (:result query)) [])
        raw           (and (= (count breakouts) 0) (= (count aggregations) 0))
        columns_metadata (if raw (add-column-metadata fields)
                                 (add-column-metadata (concat breakouts (get aggregations 0))))
        extracted-fields (if raw
                           (extract-fields rows fields)
                           (aggregate rows aggregations breakouts))]
    (log/info "Columns metadata: " columns_metadata)
    (log/info "rows-path: " rows-path)
    (log/info "rows: " rows)
    (log/info "fields: " fields)

    (respond {:cols columns_metadata } extracted-fields)))

