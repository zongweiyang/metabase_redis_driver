(defproject metabase/redis1-driver "1.0.0-2.19.1do"
  :min-lein-version "2.5.0"

  :dependencies
  [[org.clojure/core.logic "0.8.11"
    :exclusions
    [org.clojure/clojure]]
   [json-path/json-path "2.1.0"]
   [com.taoensso/carmine "2.19.1"]]

  :jvm-opts
  ["-XX:+IgnoreUnrecognizedVMOptions"]

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure "1.9.0"]
     [metabase-core "1.0.0-SNAPSHOT"]]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "redis1.metabase-driver.jar"}})

