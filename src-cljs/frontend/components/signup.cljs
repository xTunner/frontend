(ns frontend.components.signup
  (:require [frontend.components.common :as common]
            [frontend.utils.github :as gh-util]
            [frontend.utils :as utils :include-macros true]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.stefon :as stefon]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn signup [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:section
         [:div#signup.container-fluid
          [:div.row
           [:div.col-md-1]
           [:div.authorize-instructions.col-md-5
            [:h1 "Authorize with GitHub first."]
            [:p.github-signup-exp "Signing up using your GitHub login helps you start quickly."]
            [:a.btn.btn-cta.authorize-button
             {:href (gh-util/auth-url :destination "/")
              :on-click #(raise! owner [:track-external-link-clicked
                                        {:event :oauth-authorize-clicked
                                         :properties {:oauth-provider "github"}}])}
             (common/ico :github) "Authorize with GitHub"]]
           [:div.github-example.col-md-5
            [:img {:src (stefon/asset-path "/img/outer/signup/github-example.png")}]
            [:p "What you'll see next"]]
           [:div.col-md-1]]
          [:div.row.footer
           [:div.github-example.col-md-12
            [:a {:href "/privacy"} "Privacy"]
            [:a {:href "/contact"} "Contact Us"]
            [:a {:href "/"} "© CircleCI"]]]]]))))
