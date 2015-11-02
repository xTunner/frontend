(ns frontend.components.signup
  (:require [frontend.components.common :as common]
            [frontend.utils.github :as gh-util]
            [frontend.utils :as utils :include-macros true]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.stefon :as stefon]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn control [app owner]
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
                                  {:event "oauth_authorize_click"
                                   :properties {"oauth_provider" "github"}
                                   :path (gh-util/auth-url :destination "/")}])}
       (common/ico :github) "Authorize with GitHub"]]
     [:div.github-example.col-md-5
      [:img {:src (stefon/asset-path "/img/outer/signup/github-example.png")}]
      [:p "What you'll see next"]]
     [:div.col-md-1]]
    [:div.row.footer
     [:div.github-example.col-md-12
      [:a {:href "/privacy"} "Privacy"]
      [:a {:href "/contact"} "Contact Us"]
      [:a {:href "/"} "© CircleCI"]]]]])

(defn trust-marks [app owner]
  [:section
   [:div#signup.container-fluid.trust-marks-treatment
    [:div.row
     [:div.col-md-1]
     [:div.authorize-instructions.col-md-5
      [:h1 "Authorize with GitHub first."]
      [:p.github-signup-exp "Signing up using your GitHub login helps you start quickly."]
      [:a.btn.btn-cta.authorize-button
       {:href (gh-util/auth-url :destination "/")
        :on-click #(raise! owner [:track-external-link-clicked
                                  {:event "oauth_authorize_click"
                                   :properties {"oauth_provider" "github"}
                                   :path (gh-util/auth-url :destination "/")}])}
       (common/ico :github) "Authorize with GitHub"]]
     [:div.col-md-1]
     [:div.col-md-4
      [:div.row.testimonial-card
       [:div.photo-logo-row
        [:div.customer-photo.rohan-singh]
        [:div.logo.spotify]]
       [:div.customer [:p "Rohan Singh, Senior Infrastructure Engineer at Spotify"]]
       [:div.testimonial [:p "\"We love CircleCI's integration with GitHub and their ease of use. They've created a great user experience that makes it easy to see what's going on with builds and what the status is.\""]]]
      [:div.row.testimonial-card
       [:div.photo-logo-row
        [:div.customer-photo.matt-kemp]
        [:div.logo.sprig]]
       [:div.customer [:p "Matt Kemp, Co-founder & Head of Engineering at Sprig"]]
       [:div.testimonial [:p "\"CircleCI is an excellent product and was the clear winner over the alternatives when I tried them all out. With CircleCI, I was able to get a green build the fastest and hit the fewest bumps.\""]]]]
     [:div.col-md-1]]
    [:div.row.footer
     [:div.github-example.col-md-12
      [:a.footer-link {:href "/privacy"} "Privacy"]
      [:a.footer-link {:href "/contact"} "Contact Us"]
      [:a.footer-link {:href "/"} "© CircleCI"]]]]])

(defn status-bar [app owner]
  [:section
   [:div#signup.container-fluid.status-bar-treatment
    [:div.row
     [:div.col-md-1]
     [:div.authorize-instructions.col-md-5
      [:div.row
       [:div.col-xs-12.status-row
        [:img.status-bar {:src (utils/cdn-path "/img/outer/signup/status-bar.svg")}]]]
      [:div.status-messages.row
       [:div.col-xs-4.left [:p.status.success "Begin Signup"]]
       [:div.col-xs-4.middle [:p.status "Add Code"]]
       [:div.col-xs-4.right [:p.status "Ship Faster"]]]
      [:h1.header "Great, let's add some code to test."]
      [:p.sub-header "Signing up with CircleCI is " [:b "free"] ". Next, you'll be taken to Github to authenticate so you can start shipping faster."]
      [:a.btn.btn-cta.authorize-button
       {:href (gh-util/auth-url :destination "/")
        :on-click #(raise! owner [:track-external-link-clicked
                                  {:event "oauth_authorize_click"
                                   :properties {"oauth_provider" "github"}
                                   :path (gh-util/auth-url :destination "/")}])}
       (common/ico :github) "Add code from GitHub"]]

     [:div.github-example.col-md-5
      [:img {:src (stefon/asset-path "/img/outer/signup/github-example.png")}]
      [:p "What you'll see next"]]
     [:div.col-md-1]] 
    [:div.row.footer
     [:div.github-example.col-md-12
      [:a {:href "/privacy"} "Privacy"]
      [:a {:href "/contact"} "Contact Us"]
      [:a {:href "/"} "© CircleCI"]]]]])

(defn signup [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        (condp = :status-bar;;(om/get-shared owner [:ab-tests :auth-page-test])
          :control (control app owner)
          :trust-marks (trust-marks app owner)
          :status-bar (status-bar app owner))))))
