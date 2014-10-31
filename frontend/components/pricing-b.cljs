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
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [dommy.macros :refer [sel1 node]])
  (:import [goog.ui IdGenerator]))

(defn pricing [app owner]
  (reify
    om/IDisplayName (display-name [_] "Pricing B")
    om/IRender
    (render [_]
      (html
        [:div.pricing.page
         (common/nav)
         [:div.torso
          [:section.pricing-intro
           [:article
            [:h1 "Transparent pricing, built to scale."]
            [:h3 "The first container is free and each additional one is $50/mo."]]
           [:div.pricing-calculator
            [:div.pricing-calculator-controls
             [:div.controls-containers
              [:h2 "Number of Containers"]
              [:div.containers-range
               [:figure.range-back]
               [:figure.range-highlight]
               [:figure.range-knob]]]
             [:div.controls-parallelism
              [:h2 "Amount of Parallelism"]
              [:button "1x"]
              [:button "4x"]
              [:button "8x"]
              [:button "12x"]
              [:button "16x"]]]
            [:div.pricing-calculator-preview
             [:h5 "Estimated Cost"]
             [:div.calculator-preview-item
              [:div.item "Repos"]
              [:div.value "0"]]
             [:div.calculator-preview-item
              [:div.item "Builds"]
              [:div.value "0"]]
             [:div.calculator-preview-item
              [:div.item "Users"]
              [:div.value "0"]]
             [:div.calculator-preview-item
              [:div.item "Max Parallelism"]
              [:div.value "0"]]
             [:div.calculator-preview-item
              [:div.item "Concurrent Builds"]
              [:div.value "0"]]
             [:div.calculator-preview-item
              [:div.item "Cost"]
              [:div.value "0"]]
             [:a.pricing-action {:role "button"} "Sign Up Free"]]]]]
         (common/footer)]))))
