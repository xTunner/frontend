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

(defn screenshot-dashboard [app owner]
  (reify
    om/IRender
    (render [_]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html [:div.drawing
               [:div.draw-dashboard
                [:div.draw-nav-links
                 (repeat 3 [:div.draw-nav-link])]
                ; [:div.draw-menu]
                [:div.draw-body
                 [:div.draw-header
                  (common/ico :settings-light)]
                 [:div.draw-recent-builds
                  [:ul
                   (repeat 13
                    [:li
                     [:div.draw-text]
                     [:div.draw-text]
                     [:div.draw-text]
                     [:div.draw-text]
                     [:div.draw-status]])]]]]])))))

; (def ico-paths
;   {:slim_circle "M49.5,50a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0"
;    :slim_check "M35,80 L5,50 M95,20L35,80"
;    :slim_times "M82.5,82.5l-65-65 M82.5,17.5l-65,65"
;    :repo "M44.,55.6,5.64,50h-5.6v5.6h5.6V50z M44.4,38.8h-5.6v5.6h5.6V38.8z"})

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
                 ; [:div.screenshot]
                 ; (screenshot-dashboard)
                 (om/build screenshot-dashboard app)]
                [:div.purpose-articles
                 ;; [:h1 "Releases are Dead"]
                 [:article
                  [:h1 "Launches are dead, long live rapid iteration."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum convallis varius porttitor. Mauris rhoncus tempor scelerisque. Donec in dignissim erat, a venenatis arcu. Mauris sagittis volutpat nulla."]]]]
               [:section.home-practice]
               [:section.home-potential]
               [:section.home-epilog]])))))
