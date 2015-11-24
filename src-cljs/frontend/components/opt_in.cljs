(ns frontend.components.opt-in
  (:require [om.core :as om :include-macros true]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.core :as t]
            [frontend.async :refer [raise!]]
            [frontend.components.forms :refer [managed-button]])
  (:require-macros [frontend.utils :refer [html]]))

(defn ui-v2-opt-in-banner [{} owner]
  (reify
    om/IDisplayName (display-name [_] "UI V2 Opt In")
    om/IRender
    (render [_]
      (html
       [:div.ui-v2-opt-in {}
        [:div.ui-v2-opt-in-wrapper
         [:div
          [:b "Try our new look. "]
          [:span "You have been selected for a private beta of our new interface. "]]
         [:div.opt-in-right
          [:button {:on-click #(raise! owner [:try-ui-v2-clicked])} "Try our new look"]]]]))))

(defn ui-v2-opt-out-ui [{} owner]
  (reify
    om/IDisplayName (display-name [_] "UI V2 Opt In")
    om/IRender
    (render [_]
      (html
       [:div.ui-v2-opt-out {}
        (managed-button [:button {:on-click #(raise! owner [:disable-ui-v2-clicked])} "Back to old look"])
        [:a {:href "mailto:beta@circleci.com?subject=New Look"
             :target "_blank"
             :on-click #(raise! owner [:ui-v2-beta-feedback])}
         "Beta Feedback"]
        ]))))

(defn ios-reminder-banner []
  (reify
    om/IDisplayName (display-name [_] "iOS Beta End Message")
    om/IRender
    (render [_]
      (html
       [:div.ui-v2-opt-in {}
        [:div.ui-v2-opt-in-wrapper
         [:div
          "As a reminder, the iOS beta is ending as of Monday, November 30th. If you have not already, please confirm a plan to lock-in pricing and ensure a smooth transition to the limited-release "
          [:a {:href "http://circleci.com/pricing"} "here"]
          ". Reach out to "
          [:a {:href "sayhi@circleci.com"} "sayhi@circleci.com"]
          " with any questions!"]]]))))
