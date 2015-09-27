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
      (let [orgs (get-in app state/user-organizations-path)
            current-org (get-in app state/org-data-path)]
        (html [:div#top-nav
               [:div.dropdown {}
                [:button {:class "dropdown-toggle" :data-toggle "dropdown"}
                 "FuturePerfect Entity"
                 [:i.fa.fa-angle-down]]
                [:ul.dropdown-menu
                 [:li {:role "presentation"}
                  [:a {:role "menuitem"
                       :tabIndex "-1"
                       :href "/features"}
                   "Entity 1"]]
                 [:li {:role "presentation"}
                  [:a {:role "menuitem"
                       :tabIndex "-1"
                       :href "/mobile"}
                   "Hover Entity"]]
                 [:li {:role "presentation"}
                  [:a {:role "menuitem"
                       :tabIndex "-1"
                       :href "/integrations/docker"}
                   "Selected Entity"]]
                 [:li {:role "presentation"}
                  [:a {:role "menuitem"
                       :tabIndex "-1"
                       :href "/enterprise"}
                   "Another Entity"]]
                 [:li {:role "presentation"}
                  [:a {:role "menuitem"
                       :tabIndex "-1"
                       :href "/add-projects"}
                   [:i {:class "blue-plus"}]
                    "Add Entity"]]]]])))))
