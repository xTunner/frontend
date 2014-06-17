(ns frontend.components.org-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.routes :as routes]
            [frontend.datetime :as datetime]
            [frontend.models.organization :as org-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.components.common :as common]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn sidebar [{:keys [subpage plan]} owner]
  (reify
    om/IRender
    (render [_]
      (letfn [(nav-links [templates]
                (map (fn [{:keys [page text]} msg]
                       [:li {:class (when (= page subpage) :active)}
                        [:a {:href (str "#" (name page))} text]])
                     templates))]
        (html [:div.span3
               [:ul.nav.nav-list.well
                [:li.nav-header "Organization settings"]
                [:li.divider]
                [:li.nav-header "Overview"]
                (nav-links [{:page :projects :text "Projects"}
                            {:page :users :text "Users"}])
                [:li.nav-header "Plan"]
                (when plan
                  (if (frontend.models.plan/can-edit-plan? plan)
                    (nav-links [{:page :containers :text "Add containers"}
                                {:page :organizations :text "Organization"}
                                {:page :billing :text "Billing info"}
                                {:page :cancel :text "Cancel"}])
                    (nav-links [{:page :plan :text "Choose plan"}])))]])))))

(defn non-admin-plan [{:keys [org-name login]} owner]
  (reify
    om/IRender
    (render [_]
      (html [:div.row-fluid.plans
             [:div.span12
              [:h3
               "Do you want to create a plan for an organization that you don't admin?"]
              [:ol
               [:li
                "Sign up for a plan from your "
                [:a {:href (routes/v1-org-settings-subpage {:org-id login
                                                            :subpage "plan"})}
                 "\"personal organization\" page"]]
               [:li
                "Add " org-name
                " to the list of organizations you pay for or transfer the plan to "
                org-name " from the "
                [:a {:href (routes/v1-org-settings-subpage {:org-id login
                                                            :subpage "organizations"})}
                 "plan's organization page"]
                "."]]]]))))

(defn users [data owner]
  (reify
    om/IRender
    (render [_]
      (let [users (get-in data state/org-users-path)
            projects (get-in data state/org-projects-path)
            org-name (get-in data state/org-name-path)
            projects-by-follower (utils/inspect (org-model/projects-by-follower projects))
            sorted-users (sort-by (fn [u]
                                    (- (count (get projects-by-follower (:login u)))))
                                  users)]
        (html
         [:div.row-fluid.users
          [:h2
           "CircleCI users in the " org-name " organization"]
          [:div.span10
           (if-not (seq users)
             [:h4 "No users found."])
           [:div.row-fluid
            (for [user sorted-users
                  :let [login (:login user)
                        followed-projects (get projects-by-follower login)]]
              [:div.span6.well
               {:class (if (zero? (count followed-projects))
                         "fail"
                         "success")}
               [:div.row-fluid
                [:div.span2
                 [:img.gravatar {:src (gh-utils/gravatar-url {:size 45 :login login
                                                              :github-id (:github_id user)
                                                              :gravatar_id (:gravatar_id user)})}]]
                [:div.span10
                 [:h4
                  (if (seq followed-projects)
                    (str login " is following:")
                    (str login " is not following any " org-name  " projects"))]
                 (for [project (sort-by (fn [p] (- (count (:followers p)))) followed-projects)
                       :let [vcs-url (:vcs_url project)]]
                   [:div.row-fluid
                    [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                             :repo (vcs-url/repo-name vcs-url)})}
                     (vcs-url/project-name vcs-url)]])]]])]]])))))

(def main-component
  {:users users
   :projects projects
   :plan plan
   :containers containers
   :organizations organizations
   :billing billing
   :cancel cancel})

(defn org-settings [data owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (get data :org-settings-subpage)
            org-data (get-in data state/org-data-path)
            plan (get-in data state/org-plan-path)]
        (html [:div.container-fluid.org-page
               (if-not (:name org-data)
                 [:div.loading-spinner common/spinner]
                 [:div.row-fluid
                  (om/build sidebar {:subpage subpage :plan plan})
                  [:div.span9
                   (common/flashes)
                   [:div#subpage
                    [:div
                     (if (:authorized? org-data)
                       (om/build (get main-component subpage projects) data)
                       [:div (om/build non-admin-plan
                                       {:login (get-in data [:current-user :login])
                                        :org-name (:org-settings-org-name data)
                                        :subpage subpage})])]]]])])))))
