(ns circle.ruby
  (:refer-clojure :exclude [eval send methods])
  (:import org.jruby.RubySymbol)
  (:import java.io.PrintWriter)
  (:import java.io.StringWriter)
  (:import java.lang.ref.WeakReference)
  (:use [circle.util.except :only (throw-if-not)])
  (:require [clojure.string :as string])
  (:require fs))

(declare eval ruby get-class get-module send)

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

;; Each of these must return an IRubyObject.
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
  org.jruby.runtime.builtin.IRubyObject [e]
  e)

;; RaiseException wraps an exception for Java, but isn't an IRubyObject.
(defmethod ->ruby
  org.jruby.exceptions.RaiseException [e]
  (.getException e))

;; Exceptions aren't IRubyObjects
(defmethod ->ruby
  java.lang.Exception [e]
  (let [w (StringWriter.)
        pw (PrintWriter. w)]
    (.printStackTrace e pw)
    (.toString w)))

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
  org.bson.types.ObjectId [id]
  (send (get-class (get-module "BSON") "ObjectId") :from_string (.toString id)))

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

(defmethod ->ruby
  :default [val]
  (org.jruby.RubyString/newString (ruby) (format "Uncastable values: %s" val)))

(def sample-objectid-string "4ee6911fe4b05e6a4d3605fe")

(defn rspec
  "runs rspec. Useful from clojure repl.

Note that rspec will run in whatever RAILS_ENV you started in, so you
  probably want to start in RAILS_ENV=test, or rspec will clear your
  DB, or tests will fail because they assume the DB cleaner runs."
  [& args]
  (let [options (filter #(= (get % 0) \-) args)
        subdirs (remove #(= (get % 0) \-) args)
        subdirs (if (empty? subdirs) [""] subdirs)
        subdirs (map #(fs/join "spec" %) subdirs)
        command (format "
require 'rubygems'
require 'rspec/core/rake_task'

# http://blog.thefrontiergroup.com.au/2011/03/reloading-factory-girl-factories-in-the-rails-3-console
puts 'Reloading factories'
FactoryGirl.factories.clear
FactoryGirl.find_definitions
RSpec.world.reset
RSpec.world.shared_example_groups.clear

RSpec::Core::Runner.run([\"%s\"])
" (string/join "\", \"" (concat options subdirs)))]
    (eval command)))

(defn get-kernel
  "Returns the Kernel module. Used for 'core' functions like puts"
  []
  (.getKernel (ruby)))

(defn get-class
  "Returns the class/module with the given name. With one arg, looks for a class in the root namespace. With two args, looks for a class/module defined under another class, like Foo::Bar"
  ([name-str]
     (.getClass (ruby) (name name-str)))
  ([parent name-str]
     (.getClass parent (name name-str))))

(defn send
  "Call a method on a ruby object"
  [obj method & args]
  (try
    (throw-if-not obj "Can't call methods on nil")
    (.callMethod obj (name method) (into-array org.jruby.runtime.builtin.IRubyObject (map ->ruby args)))
    (catch org.jruby.exceptions.RaiseException e
      (throw (Exception. (capture-exception-data e))))))

(defn get-module
  ([name]
     (.getModule (ruby) name))
  ([parent module-name]
     (send parent :const_get module-name)))

(defn ->instance
  "Takes an object with an _id and fetches the ruby model's instance for that variable"
  [class obj]
  (let [id (-> obj :_id)
        rid (->ruby id)
        class (get-class class)]
    (send class :find rid)))

(defn methods
  "Returns the list of ruby methods on the obj"
  [obj]
  (seq (send obj :methods)))