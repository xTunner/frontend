(ns proto.repl
  (:use [clojure.contrib.with-ns :only (with-ns)]))

(defn init []
  (with-ns 'user
    (use 'clojure.repl)
    (use '[clojure.contrib.ns-utils :only (docs)])
    (use '[clojure.contrib.repl-utils :exclude (apropos source)])
    (require '[clojure.contrib.zip-filter :as zf]
             '[clojure.zip :as zip]
             '[clojure.contrib.zip-filter.xml :as zf-xml]
             '[clojure.xml :as xml]))
  (println "repl/init done"))