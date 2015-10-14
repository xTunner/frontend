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
        (if false
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
              [:a {:href "/"} "© CircleCI"]]]
            ]]
          [:section
           [:div#new-signup.container-fluid
            [:div.row
             [:div.col-md-1]
             [:div.authorize-instructions.col-md-5
              [:div.status-bar.row
               [:div.col-xs-3.left.no-padding
                [:div.success.circle [:i.fa.fa-check.fa-inverse]]
                [:div.success.line]]
               [:div.col-xs-6.middle.no-padding
                [:div.line]
                [:div.circle]
                [:div.line]]
               [:div.col-xs-3.right.no-padding
                [:div.line] 
                [:div.circle]]]
              [:div.status-messages.row
               [:div.col-xs-4.left [:p.success "Begin Signup"]]
               [:div.col-xs-4.middle [:p "Add Code"]]
               [:div.col-xs-4.right [:p "Ship Faster"]]]
              [:h1.header "Great, let's add some code to test."]
              [:p.sub-header "Signing up with CircleCI is " [:b "free"] ". Next, you'll be taken to Github to authenticate so you can start shipping faster."]
              [:a.btn.btn-cta.authorize-button
               {:href (gh-util/auth-url :destination "/")
                :on-click #(raise! owner [:track-external-link-clicked
                                          {:event "oauth_authorize_click"
                                           :properties {"oauth_provider" "github"}
                                           :path (gh-util/auth-url :destination "/")}])}
               (common/ico :github) "Add code from GitHub"]]
             [:div.col-md-1]
             [:div.col-md-4
              [:div.row.testimonial-card
                [:div.photo-logo-row
                 [:div.customer-photo.rohan-singh]
                 [:div.logo.spotify]]
                [:div.customer [:p "Rohan Singh, Senior Infrastructure Engineer at Spotify"]]
                [:div.testimonial [:p "We love CircleCI's integration with GitHub and their ease of use. They've created a great user experience that makes it easy to see what's going on with builds and what the status is. Another thing that's been indispensable is the ability to SSH into build containers, which is something you don't get with other services."]]]
              [:div.row.testimonial-card
                [:div.photo-logo-row
                 [:div.customer-photo.matt-kemp]
                 [:div.logo.sprig]]
                [:div.customer [:p "Matt Kemp, Co-founder & Head of Engineering at Sprig"]]
                [:div.testimonial [:p "CircleCI is an excellent product and was the clear winner over the alternatives when I tried them all out. I signed up for a few different CI services and got our project building, then tried to configure a few extra options. With CircleCI, I was able to get a green build the fastest and hit the fewest bumps."]]]]
             [:div.col-md-1]]
            [:div.row.footer
             [:div.github-example.col-md-12
              [:a {:href "/privacy"} "Privacy"]
              [:a {:href "/contact"} "Contact Us"]
              [:a {:href "/"} "© CircleCI"]]]]])))))
