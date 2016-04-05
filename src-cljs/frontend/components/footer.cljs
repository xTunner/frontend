(ns frontend.components.footer
  (:require [frontend.config :as config]
            [frontend.utils.html :refer [open-ext]]))

(defn footer []
  [:footer.footer
    [:div.container
      [:div.row
        [:div.col-md-5
          [:p.lead "Over "
            [:b "100,000"] " organizations and developers trust CircleCI."]
          [:span "CircleCI's continuous integration and deployment platform helps software teams rapidly release code they trust by automating the build, test, and deploy process. Built for developers, CircleCI offers a modern software development platform that lets teams ramp quickly, scale easily, and build confidently every day."]
          [:div.social-btns
            [:a.social-btn-rss {:href "http://blog.circleci.com/", :data-text , :data-url}
             [:div
              [:i.fa.fa-rss]]]
            [:a.social-btn-facebook {:href "https://www.facebook.com/circletest", :data-text , :data-url}
             [:div
              [:i.fa.fa-facebook-official]]]
            [:a.social-btn-twitter {:href "https://twitter.com/circleci", :data-text , :data-url}
             [:div
              [:i.fa.fa-twitter]]]
            [:a.social-btn-github {:href "https://github.com/circleci", :data-text , :data-url}
             [:div
              [:i.fa.fa-github]]]
            [:a.social-btn-linkedin {:href "https://www.linkedin.com/company/circleci", :data-text , :data-url}
             [:div
              [:i.fa.fa-linkedin]]]]]
        [:nav
          [:div.col-md-2.col-md-offset-1
            [:h6 "CircleCI"]
            [:ul.list-unstyled
              [:li [:a (open-ext {:href "/about/"}) "About Us"]]
              [:li [:a (open-ext {:href "/about/team/"}) "Team"]]
              [:li [:a (open-ext {:href "/press/"}) "Press"]]
              [:li [:a (open-ext {:href "/jobs/"}) "Jobs"]]
              [:li [:a {:href "http://blog.circleci.com"} "Blog"]]]]
          [:div.col-md-2
            [:h6 "Product"]
            [:ul.list-unstyled
              [:li [:a (open-ext {:href "/features/"}) "Features"]]
              [:li [:a (open-ext {:href "/mobile/"}) "Mobile"]]
              [:li [:a (open-ext {:href "/enterprise/"}) "Enterprise"]]
              [:li [:a (open-ext {:href "/pricing/"}) "Pricing"]]
              [:li [:a (open-ext {:href "/customers/"}) "Customers"]]
              [:li [:a (open-ext {:href "/changelog"}) "Changelog"]]]]
          [:div.col-md-2
            [:h6 "Support"]
            [:ul.list-unstyled
              [:li [:a (open-ext {:href "/docs"}) "Documentation"]]
              [:li [:a {:href "https://discuss.circleci.com/"} "Discuss"]]
              [:li [:a (open-ext {:href "/security/"}) "Security"]]
              [:li [:a (open-ext {:href "/privacy/"}) "Privacy"]]
              [:li [:a (open-ext {:href "/contact/"}) "Contact Us"]]]]]]
      [:div.row
        [:div.col-md-5.additional-links
          [:span "Copyright © 2015 CircleCI"]]]]])
