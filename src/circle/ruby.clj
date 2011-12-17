(ns circle.ruby
  (:refer-clojure :exclude [eval])
  (:import org.jruby.RubySymbol)
  (:import java.lang.ref.WeakReference)
  (:use midje.sweet))

;; This is the runtime all ruby requests will go through. It's
;; unlikely that Rails' runtime will be returned by getGlobalRuntime,
;; meaning that all rails variables and monkeypatching will not be
;; visible. To make them visible, call init from rails first.

;; Use a weakref to prevent this atom from keeping the ruby instance alive
(def runtime (atom (WeakReference. (org.jruby.Ruby/getGlobalRuntime))))

(defn init
  "Call this from the rails runtime, passing in JRuby.runtime. This
  will be used for all calls."
  [r]
  (swap! runtime (constantly (WeakReference. r))))

(defn ruby []
  (-> runtime deref (.get)))

(defn eval [s]
  (-> (ruby) (.evalScriptlet s)))

(defmulti ->ruby
  "Convert Ruby data to Clojure data"
  class)

(defmethod ->ruby
  clojure.lang.IPersistentMap [m]
  (let [new-h (org.jruby.RubyHash. (ruby))]
    (->> m
         (map (fn [[k v]] [(->ruby k) (->ruby v)]))
         (into {})
         (.putAll new-h))
    new-h))

(defmethod ->ruby
  clojure.lang.Keyword [k]
  (RubySymbol/newSymbol (ruby) (name k)))

(defmethod ->ruby
  clojure.lang.Sequential [v]
  (let [new-a (org.jruby.RubyArray/newArray (ruby) [])
        values (map ->ruby v)]
    (.addAll new-a values)
    new-a))

(defmethod ->ruby
  java.lang.String [s]
  (org.jruby.RubyString/newString (ruby) s))

(defmethod ->ruby
  java.lang.Float [n]
  (org.jruby.RubyFloat. (ruby) n))

(defmethod ->ruby
  java.lang.Double [n]
  (org.jruby.RubyFloat. (ruby) n))

(defmethod ->ruby
  java.lang.Integer [n]
  (org.jruby.RubyFixnum. (ruby) n))

(defmethod ->ruby
  java.lang.Long [n]
  (org.jruby.RubyFixnum. (ruby) n))

(defmethod ->ruby
  java.lang.Boolean [b]
  (if b
    (-> (ruby) (.getTrue))
    (-> (ruby) (.getFalse))))

(defmethod ->ruby
  nil [n]
  (-> (ruby) (.getNil)))

(fact "conversions work"
  (->ruby nil) => (eval "nil")
  (->ruby "a string") => (eval "'a string'")
  (->ruby -5.0) => (eval "-5.0")
  (->ruby 172) => (eval "172")
  (->ruby (Long/MAX_VALUE)) => (eval (str Long/MAX_VALUE))
  (->ruby :foo) => (eval ":foo")
  (->ruby [5 6 7]) => (eval "[5, 6, 7]")
  (->ruby {5 6}) => (eval "{5 => 6}")
  (class (eval ":foo")) => org.jruby.RubySymbol

  ;; complex nested structures
  (->ruby [:foo "bar" 5 nil]) => (eval "[:foo, 'bar', 5, nil]")
  (->ruby '(:foo "bar" 5 nil)) => (eval "[:foo, 'bar', 5, nil]")
  (->ruby {:x "foo" :y "bar" 5 nil}) => (eval "{:x => 'foo', :y => 'bar', 5 => nil}")

  (->ruby {:foo "bar",    5 nil,     "x" 7.0,   :baa [5 "mrah" {:boo :foo}]}) =>
  (eval "{:foo => 'bar', 5 => nil, 'x' => 7.0, :baa=> [5, 'mrah', {:boo => :foo}]}"))

(defn rspec
  "runs rspec. Useful from clojure repl.

Note that rspec will run in whatever RAILS_ENV you started in, so you
  probably want to start in RAILS_ENV=test, or rspec will clear your
  DB, or tests will fail because they assume the DB cleaner runs."
  []
  (eval "
require 'rubygems'
require 'rspec/core/rake_task'

RSpec::Core::Runner.run(['spec'])
"))