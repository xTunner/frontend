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
               [:nav.home-nav
                [:a.promo "What is Continuous Integration?"]
                [:a.login "Log In"]]
               [:section.home-prolog
                [:a.home-action {:role "button"}
                 "Sign Up Free"]
                [:div.home-cover]
                [:div.home-top-shelf]
                [:div.home-avatars
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle
                   (common/ico :logo)]]]
                [:div.home-slogans
                 [:h1.slogan.proverb {:title "Your org needs a hero."
                                      :alt   "Let's just authorize first."}
                  "Your org needs a hero."]
                 [:h3.slogan.context {:title "You have a product to focus on, let Circle handle your"
                                      :alt   "Signing up using your GitHub login lets us start really fast."}
                  "You have a product to focus on, let Circle handle your"]
                 [:h3.slogan.context {:title "Continuous Integration & Deployment."
                                      :alt   "Currently, we must request permissions in bulk."}
                  "Continuous Integration & Deployment."]]
                [:div.home-bottom-shelf
                 [:a {:on-click #(put! controls-ch [:home-scroll-one-clicked])}
                  "Learn more"
                  (common/ico :chevron-down)]]]
               [:section.home-purpose
                [:div.home-drawings
                 [:div.drawing]
                 [:div.drawing]
                 (om/build drawings/drawing-dashboard app)]
                [:div.home-articles
                 [:article
                  [:h1
                   "Launches are dead,"
                   [:br]
                   " long live iteration."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed tempus felis quis dictum mollis. Vivamus non tempor diam. Maecenas sagittis condimentum sapien. Ut sed gravida augue. Proin elementum molestie feugiat. Etiam finibus, neque a consectetur ultrices, tortor ligula blandit mi, ac ornare nisi felis ac dui. Fusce porta vel nunc sed commodo. Praesent bibendum ex hendrerit, bibendum elit et, egestas arcu."]
                  [:p
                   [:a.shopify-link
                    "See how Shopify does it"
                    (common/ico :slim-arrow-right)]]
                  [:div.quote-card
                   [:div.avatar]
                   [:p "\"CircleCI has significantly improved our testing infrastructure. We add new projects rapidly and continuous integration happens from the get-go.\""]
                   [:footer
                    [:cite
                     "John Collison"
                     [:br]
                     "Founder at Stripe"]
                    [:div.brand]]]]]]
               [:section.home-practice
                [:div.practice-tools
                 [:article
                  [:div.practice-tools-high
                   (common/ico :slim-rails)
                   (common/ico :slim-django)
                   (common/ico :slim-node)]
                  [:div.practice-tools-low
                   (common/ico :slim-ruby)
                   (common/ico :slim-python)
                   (common/ico :slim-js)
                   (common/ico :slim-java)
                   (common/ico :slim-php)]]]
                [:div.practice-articles
                 [:article
                  [:h1
                   "Devs rely on us to just work,"
                   [:br]
                   "we support the right tools."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed tempus felis quis dictum mollis. Vivamus non tempor diam. Maecenas sagittis condimentum sapien. Ut sed gravida augue. Proin elementum molestie feugiat. Etiam finibus, neque a consectetur ultrices, tortor ligula blandit mi, ac ornare nisi felis ac dui. Fusce porta vel nunc sed commodo. Praesent bibendum ex hendrerit, bibendum elit et, egestas arcu."]]]
                [:div.practice-customers
                 [:article
                  [:h5 "Trusted by"]
                  [:div.customers-logos
                   (om/build drawings/customer-logo-shopify app)
                   (om/build drawings/customer-logo-dropbox app)
                   (om/build drawings/customer-logo-square app)
                   (om/build drawings/customer-logo-newrelic app)
                   (om/build drawings/customer-logo-spotify app)]]]]
               [:section.home-potential
                [:div.home-articles
                 [:article
                  [:h1
                   "Look under the hood &"
                   [:br]
                   " check the bullet points."]
                  [:div.home-potential-bullets
                   [:ul
                    [:li "Quick & easy setup"]
                    [:li "Lightning fast builds"]
                    [:li "Deep Customization"]
                    [:li "Easy debugging"]]
                   [:ul
                    [:li "Smart notifications"]
                    [:li "Loving support"]
                    [:li "Automatic parallelization"]
                    [:li "Continuous Deployment"]]
                   [:ul
                    [:li "Build artifacts"]
                    [:li "Clean build environments"]
                    [:li "GitHub Integration"]
                    [:li "Open Source Support"]]]
                  [:div.quote-card
                   [:div.avatar]
                   [:p "\"CircleCI has significantly improved our testing infrastructure. We add new projects rapidly and continuous integration happens from the get-go.\""]
                   [:footer
                    [:cite
                     "John Collison"
                     [:br]
                     "Founder at Stripe"]
                    [:div.brand]]]]]
                [:div.home-drawings
                 [:div.drawing]
                 [:div.drawing]
                 (om/build drawings/drawing-build app)]]
               [:section.home-epilog
                [:a.home-action {:role "button"}
                 "Sign Up Free"]
                [:div.home-cover]
                [:div.home-top-shelf]
                [:div.home-avatars
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle
                   (common/ico :logo)]]]
                [:div.home-slogans
                 [:h1.slogan.proverb {:title "So, ready to be a hero?"
                                      :alt   "Let's just authorize first."}
                  "So, ready to be a hero?"]
                 [:h3.slogan.context {:title "Next you'll just need to sign in using your GitHub account."
                                      :alt   "Signing up using your GitHub login lets us start really fast."}
                  "Next you'll just need to sign in using your GitHub account."]
                 [:h3.slogan.context {:title "Still not convinced yet? Try taking the full tour."
                                      :alt   "Currently, we must request permissions in bulk."}
                  "Still not convinced yet? Try taking the "
                  [:a {:href "#"} "full tour"]
                  "."]]
                [:div.home-bottom-shelf
                 [:span.home-footer-bait
                  "About Us"
                  (common/ico :chevron-down)]
                 [:div.home-footer
                  [:div.home-footer-logo
                   (common/ico :logo)]
                  [:nav.home-footer-about
                   [:h5 "CircleCI"]
                   [:a "Tour"]
                   [:a "About"]
                   [:a "Support"]
                   [:a "Press"]
                   [:a "Jobs"]
                   [:a "Blog"]]
                  [:nav.home-footer-product
                   [:h5 "Product"]
                   [:a "Documentation"]
                   [:a "Privacy"]
                   [:a "Security"]
                   [:a "Enterprise"]
                   [:a "Changelog"]
                   [:a "Pricing"]]
                  [:nav.home-footer-contact
                   [:h5 "Contact"]
                   [:a "Twitter"]
                   [:a "Email"]
                   [:a "Support Chat"]
                   [:a "Phone"]
                   [:a "San Francisco, CA"]]]]]])))))
