(ns circle.ruby
  (:refer-clojure :exclude [eval])
  (:import javax.script.ScriptEngine)
  (:import javax.script.ScriptEngineManager)
  (:import org.jruby.embed.ScriptingContainer)
  (:import org.jruby.RubySymbol)
  (:use midje.sweet))

(defn ruby []
  (org.jruby.Ruby/getGlobalRuntime))

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