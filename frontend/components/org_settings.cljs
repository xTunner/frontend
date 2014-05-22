(ns frontend.components.org-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.routes :as routes]
            [frontend.datetime :as datetime]
            [frontend.models.user :as user-model]
            [frontend.models.repo :as repo-model]
            [frontend.components.common :as common]
            [frontend.utils :as utils :refer-macros [inspect]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn sidebar [{:keys [subpage plan]} owner opts]
  (reify
    om/IRender
    (render [_]
      (letfn [(nav-links [sects msgs selected]
                (map (fn [sect msg]
                       [:li {:class (when (= sect selected) :active)}
                        [:a {:href (str "#" sect)} msg]])
                     sects msgs))]
        (html [:div.span3
               [:ul.nav.nav-list.well
                [:li.nav-header "Organization settings"]
                [:li.divider]
                [:li.nav-header "Overview"]
                (nav-links ["projects" "users"]
                           ["Projects" "Users"]
                           subpage)
                [:li.nav-header "Plan"]
                (if (frontend.models.plan/can-edit-plan? plan)
                  (nav-links ["containers" "organizations" "billing" "cancel"]
                             ["Add containers" "Organizations" "Billing info" "Cancel"]
                             subpage)
                  (nav-links ["plan"] ["Choose plan"] subpage))]])))))

(defn non-admin-plan [{:keys [login org-id subpage]} owner opts]
  (reify
    om/IRender
    (render [_]
      (html [:div.row-fluid.plans
             [:div.span12
              [:h3
               "Do you want to create a plan for an organization that you don't admin?"]
              [:p "1) Sign up for a plan from your "
               [:a {:href (routes/v1-org-settings-subpage {:org-id login
                                                           :subpage "plan"})}
                "\"personal organization\" page"]]
              [:p "2) Add "
               [:span org-id]
               " to the list of organizations you pay for or transfer to the plan to "
               [:span org-id]
               " from the "
               [:a {:href (routes/v1-org-settings-subpage {:org-id login
                                                           :subpage "organizations"})}
                "plan's organization page"]]]]))))

(defn main-panel [subpage data]
  (om/build
    (case subpage
      "projects" org-projects)
    data))

(defn org-settings [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [subpage (get data :org-settings-subpage)
            org (get data :current-organization {:loaded false :authorized false})
            plan (get-in data [:settings :organizations (-> org :name keyword) :plan])]
        (html [:div.container-fluid.org-page
               (if (not (:loaded org))
                 [:div.loading-spinner common/spinner]
                 [:div.row-fluid
                  (om/build sidebar {:subpage subpage :plan plan})
                  [:div.span9
                   (common/flashes)
                   [:div#subpage
                    (when (:loaded org)
                      [:div
                       (if (:authorized org)
                         "Hello, world" #_[:div (main-panel subpage data)]
                         [:div (om/build non-admin-plan
                                         {:login (get-in data [:current-user :login])
                                          :org-id (:org-settings-org-name data)
                                          :subpage subpage})])])]]])])))))
