(ns frontend.components.mobile
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.components.mobile.icons :as icons]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn nav [selected]
  [:nav.mobile {}
   [:a.overview {:href "/mobile" :class (when (= :overview selected) "selected")}
    [:h5 "Overview"]]
   [:a {:href "/mobile/ios" :class (when (= :ios selected) "selected")}
    [:h5 "iOS"]]])

(defn mobile [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.product-page.mobile
         [:div.jumbotron
          [:h1 "Ship better mobile apps, faster"]
          [:p "Mobile apps live and die by their app store ratings. "
           "Nothing guarantees an app’s failure like a shipped bug and "
           "1-star reviews. Let CircleCI bring our deep knowledge and "
           "experience of Continuous Integration and Continuous Delivery "
           "to your mobile application development by automating the "
           "build, test, and deploy pipeline for your "
           [:a {:href "/mobile/ios"} "iOS"]
           " and "
           [:a {:href "/docs/android"} "Android"]
           " applications."]
          (common/sign-up-cta owner "mobile")]

         [:div.outer-section
          [:section.container
           [:div.overview
            [:h2.text-center "More testing, faster feedback, better releases."]
            [:p "The mobile development workflow can be frustrating and slow. "
             "App review times add significant delays to shipping, and prevent "
             "pushing fixes quickly to address shipped bugs and issues. It’s "
             "important to get the app built correctly to ensure a great user "
             "experience and better app ratings."]
            [:ol.steps
             [:li "Automate Build Process"]
             [:li "Create a Consistent Build Environment"]
             [:li "One Click Deployment" [:br] "(coming soon)"]]]]]

         [:div.outer-section.outer-section-condensed
          [:section.container
           [:div.how-it-works
            [:div.explanation
             [:h2 "How it works"]
             [:p "Every time you push new code to your project repo on GitHub, "
              "we automatically build and test your changes to make sure you didn’t "
              "break anything. For every green build, you can one-click deploy that "
              "successful version via Hockey, Testflight, Crashlytics or other "
              "over-the-air (OTA) deployment service (coming soon) to start "
              "collecting feedback immediately with no support from engineering."]]
            [:div.diagram shared/stories-procedure]]]]

         [:div.outer-section
          [:section.container
           [:div.feature-row
            [:div.feature
             (common/feature-icon "app-store")
             [:h3 "Improve App Store Rating"]
             [:p "Use Continuous Integration to reduce bugs so you ship "
              "great apps that your customers love."]]
            [:div.feature
             (common/feature-icon "sudo")
             [:h3 "Automate Testing"]
             [:p "Continuous Integration and Deployment fully automates the mobile "
              "app delivery process and significantly simplifies and accelerates "
              "the process of getting 5-star apps into the hands of your users."]]
            [:div.feature
             (common/feature-icon "controls")
             [:h3 "Inferred Project Setup"]
             [:p "Easily set up projects. Just like CircleCI for web apps, we infer "
              "your project settings without the developer having to do the setup. "
              "You can still set up your environment using the yml."]]
            [:div.feature
             (common/feature-icon "setup")
             [:h3 "Full Control"]
             [:p "You have full control to customize exactly what you need, whether it's your build tool, package manager, or system dependencies. If you can do it on your server, you can do it on ours."]]
            [:div.feature
             (common/feature-icon "commit")
             [:h3 "Merge With Confidence"]
             [:p "Monitor test status with every commit, and only merge when all the tests pass."
              "Feel productive instead of anxious when you press \"merge\"."]]
            [:div.feature
             (common/feature-icon "circle-success")
             [:h3 "Automate Deployment"]
             [:p "Continuous Integration and Deployment fully automates the mobile "
              "app delivery process and significantly simplifies and accelerates the "
              "process of getting 5-star apps into the hands of your users."]]]]]

         [:div.outer-section.outer-section-condensed.mobile-cta
          [:section.container-fluid
           [:div.cta-text
            [:h3.text-center "Start shipping faster, build for free using CircleCI today."]]
           [:div.cta-btn
            (common/sign-up-cta owner "mobile")]]]
         ]))))
