(ns frontend.components.landing-b
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.drawings :as drawings]
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
               [:section.home-prolog
                [:a.prolog-action {:role "button"}
                 "Sign Up Free"]
                [:div.prolog-cover]
                [:div.prolog-navigation
                 [:nav
                  [:a.promo "What is Continuous Integration?"]
                  [:a.login "Log In"]]]
                [:div.prolog-logos
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle
                   (common/ico :logo)]]]
                [:div.prolog-slogans
                 [:h1.slogan.proverb {:title "Your org needs a hero."
                                      :alt "We just need authorization first."}
                  "Your org needs a hero."]
                 [:h3.slogan.context {:title "You have a product to focus on, let Circle handle your"
                                      :alt "Why? Because signing up with your GitHub account lets Circle start your tests really fast."}
                  "You have a product to focus on, let Circle handle your"]
                 [:h3.slogan.context {:title "Continuous Integration & Deployment."
                                      :alt "Note, without fine-grained scopes, GitHub requires us to request them in bulk."}
                  "Continuous Integration & Deployment."]]
                [:div.prolog-learn
                 [:a
                  "Learn more"
                  (common/ico :chevron-down)]]]
               [:section.home-purpose
                [:div.purpose-drawings
                 [:div.drawing]
                 [:div.drawing]
                 (om/build drawings/drawing-dashboard app)]
                [:div.purpose-articles
                 [:article
                  [:h1
                   "Launches are dead,"
                   [:br]
                   " long live iteration."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum convallis varius porttitor. Mauris rhoncus tempor scelerisque. Donec in dignissim erat, a venenatis arcu. Mauris sagittis volutpat nulla."]]]]
               [:section.home-practice]
               [:section.home-potential]
               [:section.home-epilog]])))))
