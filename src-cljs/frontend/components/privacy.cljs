(ns frontend.components.privacy
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.common :as common]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn privacy [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.page.privacy
        [:div.jumbotron
         common/language-background-jumbotron
         [:section.container
          [:div.row
           [:article.center-block
            [:div.text-center
             [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]
             [:h1 "Privacy Policy"]
             [:h3 "We will never sell your private information."]]]]]]
        [:div.outer-section.privacy-policy
         [:section.container
          [:div.col-sm-12
           [:h2 "Private data"]
           [:p
            "We will never sell your private information. We don't provide your private information to people who may sell it. However, we do disclose customer information to a number of SAAS providers, in order to use their services:"]
           [:ul
            [:li
             [:a {:href "https://mailgun.net/"} "Mailgun"]
             " hosts our outgoing email, and so has a list of our customers' email addresses, and temporarily handles the output of test logs. Note that it does not store test logs; see "
             [:a
              {:href "http://mailgun.net/public/privacy"}
              "Mailgun's privacy policy"]
             " for more details. You may disable emails in your "
             [:a
              {:href "https://circleci.com/preferences"}
              "Circle preferences"]
             " to avoid this information being sent over email."]
            [:li
             [:a
              {:href "http://www.google.com/apps/intl/en/business/index.html"}
              "Google Apps"]
             " is our incoming mail host. Any information you send us by email is therefore accessible to Google. See "
             [:a
              {:href
               "http://support.google.com/a/bin/answer.py?hl=en&answer=60762"}
              "Google's privacy policy"]
             " for a detailed discussion of how they may access this."]
            [:li
             [:a {:href "https://intercom.io/"} "Intercom"]
             " provides our CRM, and we provide them with your email address, real name, join date, and some custom data. Intercom uses this information in order to provide their service, but does not share it otherwise. See "
             [:a
              {:href "http://docs.intercom.io/privacy.html"}
              "Intercom's privacy policy"]
             " for more detail. We also use Intercom for our support system, including the friendly \"Help\" button used throughout Circle, and all support via the site goes through Intercom. To avoid this, you may contact us directly through any medium on the " [:a {:href "/contact"} "contact page"] "."]
            [:li
             [:a {:href "https://mixpanel.com/"} "mixpanel"]
             " is used for SaaS marketing, including A/B testing, funnel tracking, and other conversion-rate optimization techniques. See "
             [:a
              {:href "https://mixpanel.com/privacy/"}
              "mixpanel's privacy policy"]
             " for more detail."]
            [:li
             "Circle uses "
             [:a
              {:href "http://www.google.com/analytics/"}
              "Google Analytics"]
             " to provide us with information about how people use our site. This means we use the Google Analytics tracking cookie throughout our site. "
             [:a
              {:href
               "http://www.google.co.uk/intl/en/analytics/privacyoverview.html"}
              "Google Analytics' privacy policy"]
             " discusses how they use this information."]]
           [:h2 "Changelog"]
           [:ul.privacy-changelog
            [:li
             [:span.label.label-primary "Mar 31, 2015"]
             " Remove HipChat."]
            [:li
             [:span.label.label-primary "Mar 17, 2014"]
             " Moved the security content to its own page, "
             [:a {:href "/security"} "located here"]]
            [:li [:span.label.label-primary "Nov 5, 2013"] " Add mixpanel; remove KissMetrics."]
            [:li [:span.label.label-primary "Mar 10, 2013"] " Discuss Hipchat usage."]
            [:li [:span.label.label-primary "Dec 22, 2012"] " Include KissMetrics and their privacy policy. Expand Intercom discussion."]
            [:li [:span.label.label-primary "March 26, 2012"] " Initial version."]]]]]
        [:div.bottom-cta.outer-section.outer-section-condensed
         common/language-background
         [:h2 "Start shipping faster, build for free using CircleCI today."]
         [:p.subheader "You have a product to focus on, let CircleCI handle your continuous integration and deployment."]
         (common/sign-up-cta owner "privacy")]]))))
