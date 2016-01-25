(ns frontend.devtools
  (:require [environ.core :refer [env]]))

(defmacro require-devtools! []
  (when (:devtools env)
    '(js/goog.require "frontend.devtools")))
