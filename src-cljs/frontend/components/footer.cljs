(ns frontend.components.footer
  (:require [frontend.config :as config]))


(defn footer []
  [:nav.footer-nav.container-fluid
   [:div.row
    [:div.col-sm-4.col-sm-push-1
     [:ul.list-unstyled
      [:li.header "CircleCI"]
      [:li [:a {:href "/about"} "About"]]
      [:li [:a {:href "/about"} "Team"]]
      [:li [:a {:href "/press"} "Press"]]
      (when-not (config/enterprise?)
        [:li [:a {:href "/jobs"} "Jobs"]])
      [:li [:a {:href "http://blog.circleci.com"} "Blog"]]]]
    [:div.col-sm-3
     [:ul.list-unstyled
      [:li.header "Product"]
      ;; TODO: features page
      [:li [:a {:href "/features"} "Features"]]
      [:li [:a {:href "/mobile"} "Mobile"]]
      [:li [:a {:href "/enterprise"} "Enterprise"]]
      (when-not (config/enterprise?)
        [:li [:a {:href "/pricing"} "Pricing"]])
      [:li [:a {:href "/changelog"} "Changelog"]]]]
    [:div.col-sm-3
     [:ul.list-unstyled
      [:li.header "Help"]
      [:li [:a {:href "/docs"} "Documentation"]]
      [:li [:a {:href "/security"} "Security"]]
      [:li [:a {:href "/privacy"} "Privacy"]]
      [:li [:a {:href "/about#contact"} "Contact Us"]]]]
    [:div.col-sm-1
     [:a.fa.fa-twitter
      {:title "Follow CircleCI on Twitter",
       :href "https://twitter.com/circleci"}]]]])
