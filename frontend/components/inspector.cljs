(ns frontend.components.inspector
  (:require [ankha.core :as ankha]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [draggable.core :as dnd]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn inspector [app owner opts]
  (reify
    om/IRender
    (render [_]
      (om/build ankha/inspector app))))
