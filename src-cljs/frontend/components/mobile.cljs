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
        [:section.intro.odd-section {}
         icons/htc
         icons/iphone
         [:article {}
          [:h1.mobile-tagline "Ship better mobile apps, faster."]
          [:p "Mobile apps live and die, etc FIXME"]]
         [:a.home-action.signup {:href (auth-url)
                     :role "button"
                     :on-click #(raise! owner [:track-external-link-clicked {:event "Auth GitHub"

                                                                                  :path (auth-url)}])}
         "Sign up for free"]
]
        [:section.pitch {}
         [:article {}
          [:h3 "More testing, faster feedback, better releases."]
          [:p "The mobile development workflow can be frustrating etc FIXME"]
          icons/steps]]
        [:section.workflow.odd-section {}
         [:article {}
          [:h3 "How it works"]
          [:p "Every time you push new code etc FIXME"]
          icons/workflow]]
        [:section.features {}
         [:article.feature-list
          [:div.feature
           icons/app-store
           [:h3 "Improve App Store Rating"]
           [:p "Blah blah FIXME"]]
          [:div.feature
           icons/testing
           [:h3 "Automate Testing"]
           [:p "Blah blah FIXME"]]
          [:div.feature
           icons/setup
           [:h3 "Inferred Project Setup"]
           [:p "Blah blah FIXME"]]
          [:div.feature
           icons/build-env
           [:h3 "Consistent Build Environment"]
           [:p "Blah blah FIXME"]]
          [:div.feature
           icons/commit
           [:h3 "Github Commit Status Integration"]
           [:p "Blah blah FIXME"]]
          [:div.feature
           icons/deploy
           [:h3 "Automate Deployment"]
           [:p "Blah blah FIXME"]]]]
        [:section.conclusion.odd-section {}
         icons/nexus
         [:h3 "Start shipping faster, build for free using CircleCI today."]
         [:a.signup.home-action {:href (auth-url)
                                 :role "button"
                                 :on-click #(raise! owner [:track-external-link-clicked {:event "Auth GitHub"

                                                                                  :path (auth-url)}])}
         "Sign up for free"]]]))))
