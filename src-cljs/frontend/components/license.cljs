(ns frontend.components.license
  (:require [om.core :as om :include-macros true]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.core :as t])
  (:require-macros [frontend.utils :refer [html]]))

(defn- days-until-api-time
  "Takes a string representing a time in the format the API delivers it and
  returns the number of days until now."
  [api-date-string]
  (->> api-date-string
       t-coerce/from-string
       (t/interval (t/now))
       t/in-days))

(defn- banner-contents [indicator-text message days days-description]
  (list
   [:.indicator indicator-text]
   [:.message message]
   [:.timing [:span.days days] " " days-description]
   [:a.contact-sales
    {:href "mailto:enterprise@circleci.com"}
    "Contact Sales…"]))

(defn- banner-for-license
  "Returns the banner for the given license, or nil if no banner applies."
  [license]
  (let [banner-type ((juxt :type :status) license)
        contents
        (case banner-type
          ["trial" "current"]
          (banner-contents "Trial Account"
                           "Welcome to CircleCI."
                           (days-until-api-time (:expiry_date license))
                           "days left in trial")

          ["trial" "in-violation"]
          (banner-contents "Trial Expired"
                           "Your trial period has expired."
                           (days-until-api-time (:hard_expiry_date license))
                           "days before suspension")
          nil)]
    (when contents
      [:.license-banner {:class banner-type}
       contents])))

(defn show-banner?
  "Should we show a banner for the given license?"
  [license]
  (boolean (banner-for-license license)))

(defn license-banner [license owner]
  (reify
    om/IDisplayName (display-name [_] "License Banner")
    om/IRender
    (render [_]
      (html
       (banner-for-license license)))))
