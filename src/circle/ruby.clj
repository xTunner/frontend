(ns circle.ruby
  (:refer-clojure :exclude [eval send methods])
  (:import org.jruby.RubySymbol)
  (:import java.lang.ref.WeakReference)
  (:use [circle.util.except :only (throw-if-not)])
  (:require fs)
  (:use midje.sweet))

(declare eval ruby)

(defn new-runtime []
  (let [config (doto (org.jruby.RubyInstanceConfig.)
                 (.setCompatVersion org.jruby.CompatVersion/RUBY1_9)
                 (.setJRubyHome (format "%s/.rvm/rubies/%s" (System/getenv "HOME") (System/getenv "rvm_ruby_string"))))]
    (org.jruby.Ruby/newInstance config)))

(defn eval
  ([s]
     (eval (ruby) s))
  ([runtime s]
     (try
       (-> runtime (.evalScriptlet s))
       (catch Exception e
         (.printStackTrace e)
         (throw e)))))

(defn ruby-require
  ([package]
     (ruby-require (ruby) package))
  ([runtime package]
     (eval runtime (format "require '%s'" (clojure.core/name package)))))

(defn add-loadpath [runtime path]
  (eval runtime (format "$LOAD_PATH << '%s/%s'" (System/getProperty "user.dir") path)))

(defn require-rails
  "Require rails, but don't start the webapp"
  []
  (ruby-require (ruby) (format "%s/config/environment" (System/getProperty "user.dir"))))

;; This is the runtime all ruby requests will go through. It's
;; unlikely that Rails' runtime will be returned by getGlobalRuntime,
;; meaning that all rails instance variables will not be
;; visible. To make them visible, call init from rails first.

;; Use a weakref to prevent this atom from keeping the ruby instance alive
(defonce runtime (atom (WeakReference. nil)))

(defn init
  "Call this from the rails runtime, passing in JRuby.runtime. This
  will be used for all calls."
  [r]
  (swap! runtime (constantly (WeakReference. r))))

(defn ensure-runtime []
  (when (not (-> runtime deref (.get)))
    (println "setting default runtime")
    (swap! runtime (constantly (WeakReference. (new-runtime))))))

(defn ruby []
  (ensure-runtime)
  (-> runtime deref (.get)))

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
  (->ruby "foo") => #(instance? org.jruby.RubyString %)
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
  [& subdirs]
  (let [subdirs (if (empty? subdirs) [""] subdirs)
        subdirs (map #(fs/join "spec" %) subdirs)
        command (format "
require 'rubygems'
require 'rspec/core/rake_task'

RSpec::Core::Runner.run(%s)
" (vec subdirs))]
    (eval command)))

(defn get-kernel
  "Returns the Kernel module. Used for 'core' functions like puts"
  []
  (.getKernel (ruby)))

(defn get-class
  "Returns the class/module with the given name. With one arg, looks for a class in the root namespace. With two args, looks for a class/module defined under another class, like Foo::Bar"
  ([name]
     (.getClass (ruby) name))
  ([parent name]
     (.getClass parent name)))

(defn get-module [name]
  (.getModule (ruby) name))

(defn send
  "Call a method on a ruby object"
  [obj method & args]
  (println "r/send: " args)
  (throw-if-not obj "Can't call methods on nil")
  (.callMethod obj (name method) (into-array org.jruby.runtime.builtin.IRubyObject (map ->ruby args))))

(defn methods
  "Returns the list of ruby methods on the obj"
  [obj]
  (seq (send obj :methods)))