(ns frontend.components.landing-b
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [dommy.core :as dommy]
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
            [goog.events]
            [goog.dom]
            [goog.style]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [dommy.macros :refer [sel1 node]]))

(defn mount-header-logo-scroll [owner]
  (let [logo (om/get-node owner "center-logo")
        cta (om/get-node owner "cta")
        nav-bkg (om/get-node owner "nav-bkg")
        no-cta (om/get-node owner "no-cta")
        nav-no-bkg (om/get-node owner "nav-no-bkg")
        first-fig-animate (om/get-node owner "first-fig-animate")
        second-fig-animate (om/get-node owner "second-fig-animate")
        vh (.-height (goog.style/getSize nav-bkg))
        scroll-callback #(do
                           (om/set-state! owner [:header-logo-visible] (neg? (.-bottom (.getBoundingClientRect logo))))
                           (om/set-state! owner [:header-cta-visible] (neg? (.-bottom (.getBoundingClientRect cta))))
                           (om/set-state! owner [:header-bkg-visible] (< (.-bottom (.getBoundingClientRect nav-bkg)) 70))
                           (om/set-state! owner [:header-cta-invisible] (< (.-top (.getBoundingClientRect no-cta)) vh))
                           (om/set-state! owner [:header-bkg-invisible] (< (.-top (.getBoundingClientRect nav-no-bkg)) 70))
                           (om/set-state! owner [:first-fig-animate] (< (.-bottom (.getBoundingClientRect first-fig-animate)) vh))
                           (om/set-state! owner [:second-fig-animate] (< (.-bottom (.getBoundingClientRect second-fig-animate)) vh))
                           (om/set-state! owner [:header-bkg-scroller] (min (js/Math.abs (.-top (.getBoundingClientRect nav-no-bkg)))
                                                                            (js/Math.abs (- 70 (.-bottom (.getBoundingClientRect nav-bkg)))))))]
    (om/set-state! owner [:browser-resize-key]
                   (goog.events/listen
                    (sel1 (om/get-shared owner [:target]) ".app-main")
                    "scroll"
                    scroll-callback))))

(defn calculate-scrollbar-width
  "Inserts and removes an out-of-view element to let us calculate the width of the scrollbar"
  [owner]
  (let [el (node [:div {:style "width: 100px; height: 100px; overflow: scroll; position: fixed; top: -200px"}])]
    (dommy/append! (om/get-node owner) el)
    (om/set-state! owner [:scrollbar-width] (utils/inspect (- (.-offsetWidth el) (.-clientWidth el))))
    (dommy/remove! el)))

(defn home [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (mount-header-logo-scroll owner)
      (calculate-scrollbar-width owner))
    om/IInitState
    (init-state [_]
      {:header-logo-visible false
       :header-cta-visible false
       :header-bkg-visible false
       :header-cta-invisible false
       :header-bkg-invisible false
       :first-fig-animate false
       :second-fig-animate false
       :scrollbar-width 0
       :scroll-ch (chan (sliding-buffer 1))})
    om/IRenderState
    (render-state [_ state]
      (let [ab-tests (:ab-tests app)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html [:div.home.page
               [:nav.home-nav {:style (merge {:width (if (< 0 (:scrollbar-width state))
                                                       (str "calc(100% - " (:scrollbar-width state) "px)")
                                                       "100%")}
                                             (when (> 70 (:header-bkg-scroller state))
                                               {:background-size (str "100% " (:header-bkg-scroller state) "px")}))
                               :class (concat
                                       (when (:header-logo-visible state) ["logo-visible"])
                                       (when (:header-cta-visible state) ["cta-visible"])
                                       (when (:header-bkg-visible state) ["bkg-visible"])
                                       (when (:header-bkg-invisible state) ["bkg-invisible"])
                                       (when (:header-cta-invisible state) ["cta-invisible"]))}
                [:a.promo "What is Continuous Integration?"]
                [:a.login {:href (auth-url)
                           :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                       :properties {:source "header-log-in"}
                                                                                       :path (auth-url)}])}
                 "Log In"]
                (om/build drawings/logo-circleci app)
                [:a.action {:href (auth-url)
                            :role "button"
                            :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                        :properties {:source "header-cta"}
                                                                                        :path (auth-url)}])}
                 "Sign Up Free"]]
               [:section.home-prolog {:ref "nav-bkg"}
                [:a.home-action {:href (auth-url)
                                 :role "button"
                                 :ref "cta"
                                 :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                             :properties {:source "prolog-cta"}
                                                                                             :path (auth-url)}])}
                 "Sign Up Free"]
                [:div.home-cover]
                [:div.home-top-shelf]
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
                [:div.home-avatars
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle {:ref "center-logo"}
                   (common/ico :logo)]]]
                [:div.home-bottom-shelf
                 [:a {:on-click #(put! controls-ch [:home-scroll-one-clicked])}
                  "Learn more"
                  (common/ico :chevron-down)]]]
               [:section.home-purpose {:class (when (:first-fig-animate state) ["animate"])}
                [:div.home-drawings
                 [:figure]
                 [:figure]
                 [:figure
                  (om/build drawings/drawing-dashboard app)]]
                [:div.home-articles
                 [:article {:ref "first-fig-animate"}
                  [:h1
                   "Launches are dead,"
                   [:br]
                   " long live iteration."]
                  [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed tempus felis quis dictum mollis. Vivamus non tempor diam. Maecenas sagittis condimentum sapien. Ut sed gravida augue. Proin elementum molestie feugiat. Etiam finibus, neque a consectetur ultrices, tortor ligula blandit mi, ac ornare nisi felis ac dui. Fusce porta vel nunc sed commodo. Praesent bibendum ex hendrerit, bibendum elit et, egestas arcu."]
                  [:p
                   [:a.shopify-link
                    "See how Shopify does it"
                    (common/ico :slim-arrow-right)]]]]]
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
                  [:div.customers-brands {:class (str "selected-" (get-in app state/customer-logo-position-path 0))}
                   [:a.customers-brand {:on-click #(put! controls-ch [:customer-logo-clicked {:position 0}])}
                    (om/build drawings/customer-logo-shopify app)]
                   [:a.customers-brand {:on-click #(put! controls-ch [:customer-logo-clicked {:position 1}])}
                    (om/build drawings/customer-logo-dropbox app)]
                   [:a.customers-brand {:on-click #(put! controls-ch [:customer-logo-clicked {:position 2}])}
                    (om/build drawings/customer-logo-square app)]
                   [:a.customers-brand {:on-click #(put! controls-ch [:customer-logo-clicked {:position 3}])}
                    (om/build drawings/customer-logo-newrelic app)]
                   [:a.customers-brand {:on-click #(put! controls-ch [:customer-logo-clicked {:position 4}])}
                    (om/build drawings/customer-logo-spotify app)]]
                  [:div.quote-card
                   [:p "\"CircleCI has significantly improved our testing infrastructure. We add new projects rapidly and continuous integration happens from the get-go.\""]
                   [:footer
                    [:div.avatar]
                    [:cite
                     "John Collison"
                     [:br]
                     "Founder at Stripe"]]]]]]
               [:section.home-potential {:class (when (:second-fig-animate state) ["animate"])}
                [:div.home-articles
                 [:article {:ref "second-fig-animate"}
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
                    [:li "Open Source Support"]]]]]
                [:div.home-drawings
                 [:figure]
                 [:figure]
                 [:figure
                  (om/build drawings/drawing-build app)]]]
               [:section.home-epilog {:ref "nav-no-bkg"}
                [:a.home-action {:href (auth-url)
                                 :ref "no-cta"
                                 :role "button"
                                 :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                             :properties {:source "epilog-cta"}
                                                                                             :path (auth-url)}])}
                 "Sign Up Free"]
                [:div.home-cover]
                [:div.home-top-shelf]
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
                [:div.home-avatars
                 [:div.avatars
                  [:div.avatar-github
                   (common/ico :github)]
                  [:div.avatar-circle
                   (common/ico :logo)]]]
                [:div.home-bottom-shelf
                 [:span.home-footer-bait
                  "About Us"
                  (common/ico :chevron-down)]
                 [:div.home-footer
                  [:div.home-footer-logo
                   (common/ico :logo)]
                  [:nav.home-footer-about
                   [:h5 "CircleCI"]
                   [:a {:href "/tour"} "Tour"]
                   [:a {:href "/about"} "About"]
                   [:a "Support"]
                   [:a {:href "/about#contact"} "Press"]
                   [:a {:href "/jobs"} "Jobs"]
                   [:a {:href "http://blog.circleci.com"} "Blog"]]
                  [:nav.home-footer-product
                   [:h5 "Product"]
                   [:a {:href "/pricing"} "Pricing"]
                   [:a {:href "/docs"} "Documentation"]
                   [:a {:href "/privacy"} "Privacy"]
                   [:a {:href "/security"} "Security"]
                   [:a {:href "/enterprise"} "Enterprise"]
                   [:a {:href "/changelog"} "Changelog"]]
                  [:nav.home-footer-contact
                   [:h5 "Contact"]
                   [:a {:href "https://twitter.com/circleci"} "Twitter"]
                   [:a {:href "mailto:sayhi@circleci.com"} "Email"]
                   [:a {:href ""} "Support Chat"]
                   [:a {:href ""} "Phone"]
                   [:a {:href "https://goo.gl/maps/uhkLn"} "San Francisco, CA"]]]]]])))))
