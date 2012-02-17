(ns circle.util.straight-jacket
  (:use [clojure.tools.logging :only (errorf )])
  (:use [slingshot.slingshot])
  (:require [circle.airbrake :as airbrake]))

(defmacro straight-jacket*
  [& body]
  `(try
     (try+
      (try+
       (do
         ~@body)
       (catch (or (instance? Exception ~'%) (instance? AssertionError ~'%)) e#
         (println "1")
         (airbrake/airbrake :exception e#
                            :data {:cmd (str (quote ~body))})))
      (catch (or (instance? Exception ~'%) (instance? AssertionError ~'%)) e#
        (.printStackTrace e#)
        (println "2")
        (errorf e# "straight-jacket")))
     (catch Exception e#
       (println "3")
       (println "*** Straight Jacket WTF ***"))))

(defmacro straight-jacket
  "For sections of code that are not allowed to fail. All exceptions
  will be caught, airbrake will be attempted. If airbrake fails, that
  exception will be caught, and a message logged. If that fails, just
  give up and cry about it."
  [& body]
  `(if (or (circle.env/development?) (circle.env/test?))
    (do ~@body)
    (straight-jacket* ~@body)))