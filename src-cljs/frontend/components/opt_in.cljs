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
        [:b "Try our new look. "]
        [:span "You have been selected for a private beta of our new interface. "]
        [:button {:on-click #(raise! owner [:try-ui-v2-clicked])} "Try our new look"]]))))

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
