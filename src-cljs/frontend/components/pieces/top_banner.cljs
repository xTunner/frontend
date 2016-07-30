(ns frontend.components.pieces.top-banner
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.notifications :as n]
            [frontend.utils.launchdarkly :as ld]
            [frontend.components.pieces.button :as button])
  (:require-macros [frontend.utils :refer [component html]]))

(defn banner
  ":html             HTML to go inside the banner

  :dismissable-fn    If you supply this argument, this is the function that will
                     attached to the dismissal 'x', so it should clear the banner"
  [{:keys [color inner-html dismissable-fn]}]
   (component
     (html
       [:div {:class color}
        [:div.text
         inner-html
         (when (not (nil? dismissable-fn))
           [:a.banner-dismiss
            {:on-click #(dismissable-fn)}
            [:i.material-icons "clear"]])]])))

;; Change this to banner, if it works?
#_(defn banner-mount [{:keys [color inner-html dismissable-fn owner]}]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :web-notification-banner-impression}))
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
