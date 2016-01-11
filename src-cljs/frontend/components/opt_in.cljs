(ns frontend.components.opt-in
  (:require [om.core :as om :include-macros true]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.core :as t]
            [frontend.config :as config]
            [frontend.async :refer [raise!]]
            [frontend.components.forms :refer [managed-button]])
  (:require-macros [frontend.utils :refer [html]]))

(defn ios-reminder-banner []
  (reify
    om/IDisplayName (display-name [_] "iOS Beta End Message")
    om/IRender
    (render [_]
      (html
        (when (not (config/enterprise?))
          [:div.ui-v2-opt-in {}
           [:div.ui-v2-opt-in-wrapper
            [:div
             "As a reminder, the iOS beta has ended as of Monday, November 30th. If you have not done so already, please confirm a plan to lock pricing in and ensure a smooth transition to the limited release "
             [:a {:href "http://circleci.com/account/plans"} "here"]
             ". Reach out to "
             [:a {:href "mailto:sayhi@circleci.com"} "sayhi@circleci.com"]
             " with any questions!"]]])))))
