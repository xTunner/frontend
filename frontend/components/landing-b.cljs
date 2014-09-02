(ns frontend.components.landing-b
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.shared :as shared]
            [frontend.env :as env]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]))

(defn home [app owner]
  (reify
    om/IRender
    (render [_]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html [:div.home.page
               [:section.home-intro
                [:a.intro-action-btn "Sign Up Free"]
                [:div.intro-action-overlay]
                [:nav.outer-navigation
                 [:a.latest-link "What is Continuous Integration?"]
                 [:a.login-link "Log In"]]
                [:div.intro-brand
                 (common/ico :logo)]
                [:div.intro-slogan
                 [:h1.slogan-tagline {:title "Your org needs a hero."
                                     :alt "We just need authorization first."}
                  "Your org needs a hero."]
                 [:h3.slogan-subline {:title "Testing is hard. Help your team focus on product, we'll handle your"
                                      :alt "Why? Because signing up with your GitHub account lets Circle start your tests really fast."}
                  "Testing is hard. Help your team focus on product, we'll handle your"]
                 [:h3.slogan-subline {:title "Continuous Integration & Deployment."
                                      :alt "Note, without fine-grained scopes, GitHub requires us to request them in bulk."}
                  "Continuous Integration & Deployment."]]
                ; [:a.intro-action-btn "Sign Up Free"]
                ; [:div.intro-action-overlay]
                [:div.intro-learn
                 [:a "Learn more"]]]
               [:section.home-why]
               [:section.home-how]
               [:section.home-what]
               [:section.home-close]])))))
