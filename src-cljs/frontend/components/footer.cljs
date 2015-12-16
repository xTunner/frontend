(ns frontend.components.footer
  (:require [frontend.config :as config]))


(defn footer []
  [:nav.footer-nav.container-fluid
   [:div.row
    [:div.col-sm-4.col-sm-push-1
     [:ul.list-unstyled
      [:li.header "CircleCI"]
      [:li [:a {:href "/about" :class "new-outer"} "About Us"]]
      [:li [:a {:href "/about/team" :class "new-outer"} "Team"]]
      [:li [:a {:href "/press" :class "new-outer"} "Press"]]
      (when-not (config/enterprise?)
        [:li [:a {:href "/jobs" :class "new-outer"} "Jobs"]])
      [:li [:a {:href "http://blog.circleci.com"} "Blog"]]]]
    [:div.col-sm-3
     [:ul.list-unstyled
      [:li.header "Product"]
      [:li [:a {:href "/features" :class "new-outer"} "Features"]]
      [:li [:a {:href "/mobile" :class "new-outer"} "Mobile"]]
      [:li [:a {:href "/enterprise" :class "new-outer"} "Enterprise"]]
      (when-not (config/enterprise?)
        [:li [:a {:href "/pricing" :class "new-outer"} "Pricing"]])
      [:li [:a {:href "/changelog"} "Changelog"]]]]
    [:div.col-sm-3
     [:ul.list-unstyled
      [:li.header "Help"]
      [:li [:a {:href "/docs"} "Documentation"]]
      [:li [:a {:href "/security" :class "new-outer"} "Security"]]
      [:li [:a {:href "/privacy" :class "new-outer"} "Privacy"]]
      [:li [:a {:href "/contact" :class "new-outer"} "Contact Us"]]]]
    [:div.col-sm-1
     [:a.fa.fa-twitter
      {:title "Follow CircleCI on Twitter",
       :href "https://twitter.com/circleci"
       :target "_blank"}]]]])
