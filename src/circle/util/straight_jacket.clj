(ns circle.util.straight-jacket
  (:use [clojure.tools.logging :only (errorf )])
  (:require [circle.airbrake :as airbrake]))

(defmacro straight-jacket
  "For sections of code that are not allowed to fail. All exceptions
  will be caught, airbrake will be attempted. If airbrake fails, that
  exception will be caught, and a message logged. If that fails, just
  give up and cry about it."
  [& body]
  `(try
     (try
       (try
         (do
           ~@body)
         (catch Exception e#
           (println "1")
           (airbrake/airbrake :exception e#
                              :force true
                              :data {:cmd (str (quote ~body))})))
       (catch Exception e#
         (.printStackTrace e#)
         (println "2")
         (errorf e# "straight-jacket")))
     (catch Exception e#
       (println "3")
       (println "*** Straight Jacket WTF ***"))))