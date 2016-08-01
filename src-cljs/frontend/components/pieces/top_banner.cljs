(ns frontend.components.pieces.top-banner
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.notifications :as n]
            [frontend.utils.launchdarkly :as ld]
            [frontend.components.pieces.button :as button]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn render-banner [{:keys [color inner-html dismissable-fn]}])

(defn banner
  ":color            Can be one of green, yellow, or red, the background color
                     of the banner.

   :impression       When not nil, inserts an impression tracking event

   :inner-html       HTML to go inside the banner

   :dismissable-fn   If you supply this argument, this is the function that will
                     attached to the dismissal 'x', so it should clear the banner

   :owner            Needed for tracking impressions, can be nil when no tracking
                     is happening"
  [{:keys [color inner-html impression dismissable-fn owner]}]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (not (nil? impression))
                 ((om/get-shared owner :track-event) {:event-type impression})))
    om/IRender
    (render [_]
      (component
        (html
          [:div {:class color}
           [:div.text
            inner-html
            (when (not (nil? dismissable-fn))
              [:a.banner-dismiss
               {:on-click #(dismissable-fn)}
               [:i.material-icons "clear"]])]])))))

(dc/do
  (defcard successful-banner
    (html
     [:div
      (om/build banner {:color "green"
              :inner-html [:span "A success banner."]
              :impression nil
              :dismissable-fn nil
              :owner nil})
     (om/build banner {:color "yellow"
              :inner-html [:span "A warning banner."]
              :impression nil
              :dismissable-fn nil
              :owner nil})
     (om/build banner {:color "red"
              :inner-html [:span "A dangerous banner!"]
              :impression nil
              :dismissable-fn nil
              :owner nil})]))
  (defcard green-banner-dismissable
    (html
     (om/build banner {:color "green"
              :inner-html [:span "Some inner HTML!"]
              :impression nil
              :dismissable-fn #(.log js/console "Faux dimissal function.")
              :owner nil}))))
