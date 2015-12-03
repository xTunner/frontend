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
          [:span "CircleCI is getting a new look. "]]
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
          "As a reminder, the iOS beta has ended as of Monday, November 30th. If you have not done so already, please confirm a plan to lock pricing in and ensure a smooth transition to the limited release "
          [:a {:href "http://circleci.com/pricing"} "here"]
          ". Reach out to "
          [:a {:href "mailto:sayhi@circleci.com"} "sayhi@circleci.com"]
          " with any questions!"]]]))))
