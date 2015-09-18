(ns frontend.components.license
  (:require [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(def show-banner?
  (complement nil?))

(defn license-banner [license owner]
  (reify
    om/IDisplayName (display-name [_] "License Banner")
    om/IRender
    (render [_]
      (html [:.license-banner
             [:.indicator "Trial Account"]
             [:.message "Welcome to CircleCI."]
             [:.timing [:span.days "30"] " days left in trial"]
             [:a.contact-sales
              {:href "mailto:enterprise@circleci.com"}
              "Contact Sales…"]]))))
