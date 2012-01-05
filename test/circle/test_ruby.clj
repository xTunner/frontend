(ns circle.test-ruby
  (:use circle.ruby)
  (:use [midje.sweet]))

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
  (->ruby (org.bson.types.ObjectId. sample-objectid-string)) => (eval (format "BSON::ObjectId::from_string '%s'" sample-objectid-string))

  ;; complex nested structures
  (->ruby [:foo "bar" 5 nil]) => (eval "[:foo, 'bar', 5, nil]")
  (->ruby '(:foo "bar" 5 nil)) => (eval "[:foo, 'bar', 5, nil]")
  (->ruby {:x "foo" :y "bar" 5 nil}) => (eval "{:x => 'foo', :y => 'bar', 5 => nil}")

  (->ruby {:foo "bar",    5 nil,     "x" 7.0,   :baa [5 "mrah" {:boo :foo}]}) =>
  (eval "{:foo => 'bar', 5 => nil, 'x' => 7.0, :baa=> [5, 'mrah', {:boo => :foo}]}"))