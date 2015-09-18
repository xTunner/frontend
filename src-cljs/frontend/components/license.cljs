(ns frontend.components.license
  (:require [om.core :as om :include-macros true]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.core :as t])
  (:require-macros [frontend.utils :refer [html]]))

(def show-banner?
  (complement nil?))

(defn license-banner [license owner]
  (reify
    om/IDisplayName (display-name [_] "License Banner")
    om/IRender
    (render [_]
      (let [expiry-date (t-coerce/from-string (:expiry_date license))
            days-until-expiry (t/in-days (t/interval (t/now) expiry-date))]
        (html [:.license-banner
               [:.indicator "Trial Account"]
               [:.message "Welcome to CircleCI."]
               [:.timing [:span.days days-until-expiry] " days left in trial"]
               [:a.contact-sales
                {:href "mailto:enterprise@circleci.com"}
                "Contact Sales…"]])))))
