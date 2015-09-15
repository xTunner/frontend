(ns frontend.components.top-nav
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.models.plan :as pm]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.seq :refer [select-in]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn top-nav [app owner]
  (reify
    om/IDisplayName (display-name [_] "Top Nav")
    om/IRender
    (render [_]
      (html [:div#top-nav "Fish sideburns"]))))
