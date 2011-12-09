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

(defn intern-keyword
  "Given a java string, return a ruby keyword"
  [s]
  (RubySymbol/newSymbol (ruby) s))

(defn ->array [c]
  "Convert a java.util.Collection to a Ruby array"
  (let [new-c (org.jruby.RubyArray/newArray (ruby) [])]
    (.addAll new-c c)
    new-c))

(defn ->hash [h]
  "Convert a java.util.Map to a Ruby HashMap"
  (assert (instance? java.util.Map h))
  h)

(defn ->string [s]
  "Convert a java.lang.String to a Ruby String"
  (org.jruby.RubyString/newString (ruby) s))

(fact "convert to array"
  (->array [5 6 7]) => (eval "[5, 6, 7]"))

(fact "eval returns ruby objects"
  (class (eval ":foo")) => org.jruby.RubySymbol)

(fact "intern-returns-keyword"
  (intern-keyword "foo") => (eval ":foo"))

(fact "convert to hash"
  (->hash {5 6}) => (eval "{5 => 6}"))