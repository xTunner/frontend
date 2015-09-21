(ns frontend.components.license
  (:require [om.core :as om :include-macros true]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.core :as t])
  (:require-macros [frontend.utils :refer [html]]))

(def show-banner?
  (complement nil?))

(defn- days-until-api-time
  "Takes a string representing a time in the format the API delivers it and
  returns the number of days until now."
  [api-date-string]
  (->> api-date-string
       t-coerce/from-string
       (t/interval (t/now))
       t/in-days))

(defn license-banner [license owner]
  (reify
    om/IDisplayName (display-name [_] "License Banner")
    om/IRender
    (render [_]
      (html
       (case (:status license)
         "current"
         (let [days-until-expiry (days-until-api-time (:expiry_date license))]
           [:.license-banner {:class (:status license)}
            [:.indicator "Trial Account"]
            [:.message "Welcome to CircleCI."]
            [:.timing [:span.days days-until-expiry] " days left in trial"]
            [:a.contact-sales
             {:href "mailto:enterprise@circleci.com"}
             "Contact Sales…"]])

         "in-violation"
         (let [days-until-suspension (days-until-api-time (:hard_expiry_date license))]
           [:.license-banner {:class (:status license)}
            [:.indicator "Trial Expired"]
            [:.message "Your trial period has expired."]
            [:.timing [:span.days days-until-suspension] " days before suspension"]
            [:a.contact-sales
             {:href "mailto:enterprise@circleci.com"}
             "Contact Sales…"]]))))))
