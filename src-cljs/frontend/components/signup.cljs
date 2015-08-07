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
       [:section
        [:div#signup
         [:h1 "Authorize with GitHub first."]
         [:h3 "Signup up using your GitHub login"]
         [:a.btn.btn-cta {:href (gh-util/auth-url :destination "/")
                          :on-click #(raise! owner [:track-external-link-clicked
                                                    {:event "signup_click"
                                                     :path (gh-util/auth-url :destination "/")}])}
          "Authorize with GitHub"]]]))))
