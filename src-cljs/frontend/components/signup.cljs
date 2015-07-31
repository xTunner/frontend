(ns frontend.components.signup
  (:require [frontend.utils.github :as gh-util]
            [frontend.async :refer [raise!]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn signup [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div#signup
         [:a.btn.btn-cta {:href (gh-util/auth-url :destination "/")
                          :on-click #(raise! owner [:track-external-link-clicked
                                                    {:event "signup_click"
                                                     :path (gh-util/auth-url :destination "/")}])}
          "Authorize with Github"]]))))
