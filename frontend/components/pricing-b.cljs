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
       [:h1 "hello world"]))))
