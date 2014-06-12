(ns frontend.components.placeholder
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn placeholder [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:section "You navigated to build " (pr-str (get-in app [:inspected-project]))]))))
