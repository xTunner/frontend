(ns circle.ruby
  (:import javax.script.ScriptEngine)
  (:import javax.script.ScriptEngineManager)
  (:import org.jruby.embed.ScriptingContainer)
  (:import org.jruby.RubySymbol))

(def engine
  (->
   (ScriptEngineManager.)
   (.getEngineByName "jruby")))

(defn eval-ruby
  "Eval a ruby string."
  [s]
  (-> engine
      (.eval s)))

(defn ruby
  "Returns the org.jruby.Ruby object"
  []
  (-> (ScriptingContainer.)
      (.getProvider)
      (.getRuntime)))

(defn intern-keyword
  "Given a java string, return a ruby keyword"
  [s]
  (RubySymbol/newSymbol (ruby) s))