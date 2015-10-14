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
               [:div.col-md-3.left.no-padding
                [:div.success.circle [:i.fa.fa-check.fa-inverse]]
                [:div.success.line.smaller-bottom-margin]]
               [:div.col-md-2.no-padding
                [:div.line.smaller-bottom-margin]]
               [:div.col-md-2.middle.no-padding
                [:div.line]
                [:div.circle]
                [:div.line]]
               [:div.col-md-3.no-padding
                [:div.line.smaller-bottom-margin]]
               [:div.col-md-2.right.no-padding
                [:div.line] 
                [:div.circle]]]
              [:div.status-messages.row
               [:div.col-md-4.left [:p.success "Begin Signup"]]
               [:div.col-md-4.middle [:p "Add Code"]]
               [:div.col-md-4.right [:p "Ship Faster"]]]
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
               [:div]
                [:div.customer [:p "Parker Conrad, CEO at Zenefits"]]
                [:div.testimonial [:p "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customers, not maintaining CI Infrastructure."]]
               ]
              [:div.row.testimonial-card
                [:div.customer [:p "Parker Conrad, CEO at Zenefits"]]
                [:div.testimonial [:p "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customers, not maintaining CI Infrastructure."]]]
              ]
             [:div.col-md-1]]
            [:div.row.footer
             [:div.github-example.col-md-12
              [:a {:href "/privacy"} "Privacy"]
              [:a {:href "/contact"} "Contact Us"]
              [:a {:href "/"} "© CircleCI"]]]]])))))
