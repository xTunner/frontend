(ns frontend.models.container
  (:require [frontend.datetime :as datetime]
            [frontend.models.project :as proj]
            [goog.string :as gstring]
            goog.string.format))

(defn id [container]
  (:index container))

;; XXX: implement container status style
(defn status-style [container]
  "running")
