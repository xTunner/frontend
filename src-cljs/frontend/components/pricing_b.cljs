(ns frontend.components.pricing-b
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [dommy.core :as dommy]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.drawings :as drawings]
            [frontend.components.shared :as shared]
            [frontend.env :as env]
            [frontend.scroll :as scroll]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils.seq :refer [select-in]]
            [goog.events]
            [goog.dom]
            [goog.style]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]])
  (:import [goog.ui IdGenerator]))

(def max-containers 64)

(def increment (/ 100.0 max-containers))

(defn calculate-drag-percent [owner event]
  (let [slider (om/get-node owner "pricing-range")
        width (.-width (goog.style/getSize slider))
        slider-left (goog.style/getPageOffsetLeft slider)
        event-left (.-pageX event)
        ; increment (/ 100.0 64)
        ]
    (min 100 (max 0 (* 100 (/ (- event-left slider-left) width))))))

(defn pricing [app owner]
  (reify
    om/IDisplayName (display-name [_] "Pricing B")
    om/IInitState (init-state [_] {:drag-percent 0
                                   :dragging? false})
    om/IRenderState
    (render-state [_ {:keys [drag-percent dragging?]}]
      (let [pricing-parallelism (get-in app state/pricing-parallelism-path 1)
            normalized-drag-percent (* increment (js/Math.ceil (/ drag-percent increment)))
            container-count (max pricing-parallelism (/ normalized-drag-percent increment))
            concurrent-count (int (/ container-count pricing-parallelism))
            total-cost (max (* (dec container-count) 50) 0)]
        (html
          [:div.pricing.page {:on-mouse-up #(om/set-state! owner :dragging? false)
                              :on-mouse-down #(when dragging?
                                                (om/set-state! owner :drag-percent (calculate-drag-percent owner %)))
                              :on-mouse-move #(when dragging?
                                                (om/set-state! owner :drag-percent (calculate-drag-percent owner %)))}
           (common/nav owner)
           [:div.torso
            [:section.pricing-intro
             [:article
              [:h1 "Transparent pricing, built to scale."]
              [:h3 "The first container is free and each additional one is $50/mo. "
                   [:a {:href "/enterprise"} "Need enterprise pricing?" ]]]
             [:div.pricing-calculator
              [:div.pricing-calculator-controls
               [:div.controls-containers
                [:h2 "Containers"]
                [:p "All of your containers are shared across your entire organization."]
                [:div.containers-range {:on-click #(om/set-state! owner :drag-percent (calculate-drag-percent owner %))
                                        :on-mouse-down #(om/set-state! owner :dragging? true)
                                        :ref "pricing-range"}
                 [:figure.range-back]
                 [:figure.range-highlight {:style {:width (str (* container-count increment) "%")}}]
                 [:button.range-knob {:on-mouse-down #(om/set-state! owner :dragging? true)
                                      :style {:left (str (* container-count increment) "%")}
                                      :data-count container-count}]]]
               [:div.controls-parallelism
                [:h2 "Parallelism"]
                [:p
                 [:span "You can run "]
                 [:strong {:class (when (> concurrent-count 9) "double-digits")} (str concurrent-count)]
                 [:span " concurrent builds with "]
                 [:strong {:class (when (> container-count 9) "double-digits")} (str container-count)]
                 [:span " containers and "]
                 [:strong (str pricing-parallelism)]
                 [:span " parallelism."]]
                [:div.parallelism-options
                 (for [p [1 4 8 12 16]]
                 [:button {:on-click #(do
                                        (raise! owner [:pricing-parallelism-clicked {:p p}])
                                        (om/set-state! owner :drag-percent (max drag-percent (* p increment))))
                           :class (when (= p pricing-parallelism) "active")}
                  (str p "x")])]]]
              [:div.pricing-calculator-preview
               [:h5 "Estimated Cost"]
               [:div.calculator-preview-item
                [:div.item "Repos"]
                [:div.value (common/ico :infinity)]]
               [:div.calculator-preview-item
                [:div.item "Builds"]
                [:div.value (common/ico :infinity)]]
               [:div.calculator-preview-item
                [:div.item "Users"]
                [:div.value (common/ico :infinity)]]
               [:div.calculator-preview-item
                [:div.item "Max Parallelism"]
                [:div.value (str container-count)]]
               [:div.calculator-preview-item
                [:div.item "Concurrent Builds"]
                [:div.value (str concurrent-count)]]
               [:div.calculator-preview-item
                [:div.item "Total"]
                [:div.value (if (> total-cost 0)
                              (str "$" total-cost)
                              "Free")]]
               (when (om/get-shared owner [:ab-tests :pricing_button_green])
                 [:a.pricing-action.green {:href (auth-url)
                                           :on-click #(raise! owner [:track-external-link-clicked {:path (auth-url) :event "Auth GitHub" :properties {:source "pricing page sign-up" :url js/window.location.pathname}}])
                                           :title "Sign up with Github"} "Sign Up Free"])
               (when-not (om/get-shared owner [:ab-tests :pricing_button_green])
                 [:a.pricing-action {:href (auth-url)
                                     :on-click #(raise! owner [:track-external-link-clicked {:path (auth-url) :event "Auth GitHub" :properties {:source "pricing page sign-up" :url js/window.location.pathname}}])
                                     :title "Sign up with Github"} "Sign Up Free"])]]
             [:article.pricing-features
              [:div.pricing-feature
               [:h3 "Easy Debugging"]
               [:p "When your tests are broken, we help you get them fixed. We automatically warn you about common mistakes, and document how to fix them.
                   We provide all the information you need to reproduce an error locally.
                   And if you still can't reproduce it, you can SSH into our VMs to debug it yourself."]]
              [:div.pricing-feature
               [:h3 "Continuous Deployment"]
               [:p "If your tests work, deploy straight to staging, production, or a QA server.
                   Deploy anywhere, including Heroku, DotCloud, EngineYard, etc, or using Capistrano, Fabric or custom commands."]]
              [:div.pricing-feature
               [:h3 "Personalized Notifications"]
               [:p "You don't care what John did in his feature branch, but you care what gets pushed to master.
                   Who cares if the tests pass yet again, you only want notifications when they fail.
                   Circle intelligently notifies you about the tests you care about, and does it over email, Hipchat, Campfire, FlowDock, IRC and webhooks."]]]
             [:article
              [:h3 "How do containers work?"]
              [:p "Every time you push to GitHub, we checkout your code and run your build inside of a container.
                   If you don't have enough free containers available, then your builds queue up until other builds finish."]
              [:p "Everyone gets their first container free and your team can run as many builds as you want with that container.
                   More containers allows faster builds through parallellism in addition shorter queue times"]
              [:p "Parallelism is an extrememly powerful feature and allows to dramitcally speed up your test suite.
                   CircleCI automatically splits your tests across multiple containers, finishing your build in a fraction of the time."]]
             [:article
              [:h3 "How many containers do I need?"]
              [:p "Most of our customers use about 2.5 containers per full-time developer.
                   Every team is different however and we're happy to set you up with a trial to help you figure out how many works best for you."]]]]
           (common/footer)])))))
