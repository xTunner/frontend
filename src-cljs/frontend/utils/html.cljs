(ns frontend.utils.html
  ;; need to require runtime for the macro to work :/
  (:require [hiccups.runtime :as hiccupsrt]
            [goog.dom.dataset :as data])
  (:require-macros [hiccups.core :as hiccups]))

(defn hiccup->html-str [hiccup-form]
  (hiccups/html hiccup-form))

(defn open-ext [attrs]
  "Add attributes to a link so that it won't try to render the destination page in place in the current dom."
  (assoc attrs :data-external true))

(defn external-link? [link]
  (boolean (data/get link "external")))
