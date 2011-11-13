(ns circle.repl
  (:use [clojure.contrib.with-ns :only (with-ns)])
  (:require clojure.contrib.trace))

(defn init []
  (with-ns 'user
    (use 'clojure.repl)
    (use '[clojure.contrib.ns-utils :only (docs)])
    (use '[clojure.contrib.repl-utils :exclude (apropos source)])
    (use '[circle.backend.ec2 :only (instances print-instances)]))
  (println "repl/init done"))


(require 'clojure.contrib.trace)

(defn trace-ns
  "Replaces each function from the given namespace with a version wrapped
in a tracing call. Can be undone with untrace-ns. ns should be a namespace
object or a symbol."
  [ns]
  (doseq [s (keys (ns-interns ns))
          :let [v (ns-resolve ns s)]
          :when (and (ifn? @v) (-> v meta :macro not))]
    (intern ns
            (with-meta s {:traced @v})
            (let [f @v] (fn [& args]
                          (clojure.contrib.trace/trace (str "entering: " s))
                          (apply f args))))))

(defn untrace-ns
  "Reverses the effect of trace-ns, replacing each traced function from the
given namespace with the original, untraced version."
  [ns]
  (doseq [s (keys (ns-interns ns))
          :let [v (ns-resolve ns s)]
          :when (:traced (meta v))]
    (alter-meta! (intern ns s (:traced (meta v)))
                 dissoc :traced)))