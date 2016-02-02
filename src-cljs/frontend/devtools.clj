(ns frontend.devtools
  (:require [environ.core :refer [env]]))

(defmacro require-devtools! []
  (when (:devtools env)
    ;; TODO: Replace this with a ClojureScript require function
    '(js/goog.require "frontend.devtools")))
