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
                [:nav.outer-navigation
                 [:a.latest-link "What is Continuous Integration?"]
                 [:a.login-link "Log In"]]
                [:div.intro-brand
                 (common/ico :logo)]
                [:div.intro-slogan
                 [:h1 "Your team needs a hero."]
                 [:h3 "Testing is hard. Let your org focus on product, let us handle your "]
                 [:h3 "Continuous Integration & Deployment."]]
                [:div.intro-action
                 [:div.intro-action-btn ;; "Sign Up Free"
                  [:div.signup-view]]]
                [:div.intro-learn
                 [:a "Learn more"]]]
               [:section.home-why]
               [:section.home-how]
               [:section.home-what]
               [:section.home-close]])))))
