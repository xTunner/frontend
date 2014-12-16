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

(defn mobile [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.mobile.page
        (common/nav owner)
        [:section.intro.odd-section {}
         icons/htc
         icons/iphone
         [:article {}
          [:h1.mobile-tagline "Mobile App Testing, Done Faster."]
          [:p "Mobile apps live and die by their app store ratings. "
           "Nothing guarantees an app’s failure like a shipped bug and "
           "1-star reviews. Let CircleCI bring our deep knowledge and "
           "experience of Continuous Integration and Continuous Delivery "
           "to your mobile application development by automating the "
           "build, test, and deploy pipeline for your iOS and "
           "Android applications."]]
         [:a.home-action.signup {:href (auth-url)
                     :role "button"
                     :on-click #(raise! owner [:track-external-link-clicked {:event "Auth GitHub"

                                                                                  :path (auth-url)}])}
         "Sign up for free"]
]
        [:section.pitch {}
         [:article {}
          [:h2 "More testing, faster feedback, better releases."]
          [:p "The mobile development workflow can be frustrating and slow. "
           "App review times add significant delays to shipping, and prevent "
           "pushing fixes quickly to address shipped bugs and issues. It’s "
           "important to get the app built correctly to ensure a great user "
           "experience and better app ratings."]
          icons/steps]]
        [:section.workflow.odd-section {}
         [:article {}
          [:h2 "How it works"]
          [:p "Every time you push new code to your project repo on GitHub, "
           "we automatically build and test your changes to make sure you didn’t "
           "break anything. For every green build, you can one-click deploy that "
           "successful version via Hockey, Testflight, Crashlytics or other "
           "over-the-air (OTA) deployment service (coming soon) to start "
           "collecting feedback immediately with no support from engineering."]
          icons/workflow]]
        [:section.features {}
         [:article.feature-list
          [:div.feature
           icons/app-store
           [:h3 "Improve App Store Rating"]
           [:p "Use Continuous Integration to reduce bugs so that you ship "
            "great apps that your customers love."]]
          [:div.feature
           icons/testing
           [:h3 "Automate Testing"]
           [:p "Continuous Integration and Deployment fully automates the mobile "
            "app delivery process and significantly simplifies and accelerates "
            "the process of getting 5-star apps into the hands of your users."]]
          [:div.feature
           icons/setup
           [:h3 "Inferred Project Setup"]
           [:p "Easily set up projects. Just like CircleCI for web apps, we infer "
            "your project settings without the developer having to do the setup. "
            "You can still setup your environment using the yml."]]
          [:div.feature
           icons/build-env
           [:h3 "Full Control"]
           [:p "You have full control to customize exactly what you need, whether it's your build tool, package manager, or system dependencies. If you can do it on your server, you can do it on ours."]]
          [:div.feature
           icons/commit
           [:h3 "Merge With Confidence"]
           [:p "Get updates for each build to monitor green tests."]]
          [:div.feature
           icons/deploy
           [:h3 "Automate Deployment"]
           [:p "Continuous Integration and Deployment fully automates the mobile "
            "app delivery process and significantly simplifies and accelerates the "
            "process of getting 5-star apps into the hands of your users."]]]]
        [:section.conclusion.odd-section {}
         [:a.signup.home-action {:href (auth-url)
                                 :role "button"
                                 :on-click #(raise! owner [:track-external-link-clicked {:event "Auth GitHub"
                                                                                         :path (auth-url)}])}
          "Sign up for free"]
         [:h3 "Start shipping faster, build for free using CircleCI today."]
         icons/nexus]]))))
