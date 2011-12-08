(ns circle.workers
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.config :as config])
  (:require [circle.ruby :as ruby])
  (:use midje.sweet))

; TODO: find another home for this
(defn run-build-from-jruby
  [project-name job-name]
  (let [build (config/build-from-name project-name :job-name (keyword job-name))]
    (run/run-build build)))


;;; Workers. Handles starting workers, checking if they're done, and getting the
;;; result.

(defmulti convert-to-ruby
  "Convert Ruby data to Clojure data"
  class)

(defmethod convert-to-ruby
  clojure.lang.IPersistentMap [m]
  (->> m
       (map (fn [[k v]] [(convert-to-ruby k) (convert-to-ruby v)]))
       (into {})
       (ruby/->hash)))

(defmethod convert-to-ruby
  clojure.lang.Keyword [k]
  (ruby/intern-keyword (name k)))

(defmethod convert-to-ruby
  clojure.lang.IPersistentVector [s]
  (ruby/->array (map convert-to-ruby s)))

(defmethod convert-to-ruby
  java.lang.String [s]
  (ruby/->string s))

(defmethod convert-to-ruby
  java.lang.Number [i]
  i)

(defmethod convert-to-ruby
  java.lang.Boolean [b]
  b)

(defmethod convert-to-ruby
  nil [n]
  nil)

(fact "conversions work"
  (convert-to-ruby nil) => (ruby/eval "nil")
  (convert-to-ruby "a string") => (ruby/eval "'a string'")
  (convert-to-ruby -5.0) => (ruby/eval "-5.0")
  (convert-to-ruby :foo) => (ruby/eval ":foo")
  (convert-to-ruby [:foo "bar" 5 nil]) => (ruby/eval "[:foo, 'bar', 5, nil]")

  (convert-to-ruby {:foo "bar",    5 nil,     "x" 7.0,   :baa [5 "mrah" {:boo :foo}]}) =>
       (ruby/eval "{:foo => 'bar', 5 => nil, 'x' => 7.0, :baa=> [5, 'mrah', {:boo => :foo}]}")
  (convert-to-ruby {:x "foo" :y "bar" 5 nil}) => (ruby/eval "{:x => 'foo', :y => 'bar', 5 => nil}"))

(defn call-clojure-from-ruby [f args]
  (convert-to-ruby
   (apply f args)))

(def worker-store (ref {}))

(defn start-worker [f & args]
  "Call fn with args as a worker. Returns the id of the worker"
  (let [fut (future
              (call-clojure-from-ruby f args))]
    (dosync
     (let [next-id (count @worker-store)]
       (alter worker-store assoc next-id fut)
       next-id))))

(defn fire-worker [f & args]
  "Start a worker, but don't wait for a response"
  (future (call-clojure-from-ruby f args))
  nil)

(defn blocking-worker [f & args]
  "Start a worker and block until it returns"
  (call-clojure-from-ruby f args))

(defn worker-done? [id]
  "Return if the worker is done. Throw an NPE if there is no such worker"
  (dosync
   (let [as-int (int id)
         fut (get @worker-store as-int)]
     (future-done? fut))))

(defn wait-for-worker [id]
  "Block until the worker is done, and return it's result. Will only work once, next time it throws a NPE because the worker is no longer available"
  (dosync
   (let [as-int (int id)
         fut (get @worker-store as-int)
         ; TODO: when we dereference this, it may result in throwing the
         ; exception that occurred within the future. We want the stack trace
         ; that appears on error to be that stack-trace.
         result (deref fut)]

     (alter worker-store dissoc as-int)
     result)))

(defn worker-count []
  (dosync
   (count @worker-store)))