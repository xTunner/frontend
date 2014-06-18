(ns frontend.components.org-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.routes :as routes]
            [frontend.datetime :as datetime]
            [frontend.models.organization :as org-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.style])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [dommy.macros :refer [node sel sel1]]))

(defn sidebar [{:keys [subpage plan org-name]} owner]
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
                  (if (plan-model/can-edit-plan? plan org-name)
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
            projects-by-follower (org-model/projects-by-follower projects)
            sorted-users (sort-by (fn [u]
                                    (- (count (get projects-by-follower (:login u)))))
                                  users)]
        (html
         [:div.users
          [:h2
           "CircleCI users in the " org-name " organization"]
          [:div
           (if-not (seq users)
             [:h4 "No users found."])
           [:div
            (for [user sorted-users
                  :let [login (:login user)
                        followed-projects (get projects-by-follower login)]]
              [:div.well.om-org-user
               {:class (if (zero? (count followed-projects))
                         "fail"
                         "success")}

               [:img.gravatar {:src (gh-utils/gravatar-url {:size 45 :login login
                                                            :github-id (:github_id user)
                                                            :gravatar_id (:gravatar_id user)})}]
               [:div.om-org-user-projects-container
                [:h4
                 (if (seq followed-projects)
                   (str login " is following:")
                   (str login " is not following any " org-name  " projects"))]
                [:div.om-org-user-projects
                 (for [project (sort-by (fn [p] (- (count (:followers p)))) followed-projects)
                       :let [vcs-url (:vcs_url project)]]
                   [:div.om-org-user-project
                    [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                             :repo (vcs-url/repo-name vcs-url)})}
                     (vcs-url/project-name vcs-url)]])]]])]]])))))

(defn equalize-size
  "Given a node, will find all elements under node that satisfy selector and change
   the size of every element so that it is the same size as the largest element."
  [node selector]
  (let [items (sel node selector)
        sizes (map goog.style/getSize items)
        max-width (apply max (map #(.-width %) sizes))
        max-height (apply max (map #(.-height %) sizes))]
    (doseq [item items]
      (goog.style/setSize item max-width max-height))))

(defn followers-container [followers owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (equalize-size (om/get-node owner) ".follower-container"))
    om/IDidUpdate
    (did-update [_ _ _]
      (equalize-size (om/get-node owner) ".follower-container"))
    om/IRender
    (render [_]
      (html
       [:div.followers-container.row-fluid
        [:div.row-fluid
         (for [follower followers]
           [:span.follower-container
            {:style {:display "inline-block"}}
            [:img.gravatar
             {:src (gh-utils/gravatar-url {:size 30 :login (:login follower)
                                           :gravatar_id (:gravatar_id follower)})}]
            " "
            [:span (:login follower)]])]]))))

(defn projects [data owner]
  (reify
    om/IRender
    (render [_]
      (let [users (get-in data state/org-users-path)
            projects (get-in data state/org-projects-path)
            {followed-projects true unfollowed-projects false} (group-by #(pos? (count (:followers %)))
                                                                         projects)
            org-name (get-in data state/org-name-path)]
        (html
         [:div
          [:div.followed-projects.row-fluid
           [:h2 "Followed projects"]
           (if-not (seq followed-projects)
             [:h4 "No followed projects found."]

             [:div.span8
              (for [project followed-projects
                    :let [vcs-url (:vcs_url project)]]
                [:div.row-fluid
                 [:div.span12.success.well
                  [:div.row-fluid
                   [:div.span12
                    [:h4
                     [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                              :repo (vcs-url/repo-name vcs-url)})}
                      (vcs-url/project-name vcs-url)]
                     " "
                     [:a.edit-icon {:href (routes/v1-project-settings {:org (vcs-url/org-name vcs-url)
                                                                       :repo (vcs-url/repo-name vcs-url)})}
                      [:i.fa.fa-gear]]
                     " "
                     [:a.github-icon-link {:href vcs-url}
                      [:i.fa.fa-github]]]]]
                  (om/build followers-container (:followers project))]])])]
          [:div.row-fluid
           [:h2 "Untested repos"]
           (if-not (seq unfollowed-projects)
             [:h4 "No untested repos found."]

             [:div.span8
              (for [project unfollowed-projects
                    :let [vcs-url (:vcs_url project)]]
                [:div.row-fluid
                 [:div.fail.span12.well
                  [:h4
                   [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                            :repo (vcs-url/repo-name vcs-url)})}
                    (vcs-url/project-name vcs-url)]
                   " "
                   [:a.edit-icon {:href (routes/v1-project-settings {:org (vcs-url/org-name vcs-url)
                                                                     :repo (vcs-url/repo-name vcs-url)})}
                    [:i.fa.fa-gear]]
                   " "
                   [:a.github-icon-link {:href vcs-url}
                    [:i.fa.fa-github]]]]])])]])))))

(defn plans-trial-notification [plan org-name controls-ch]
  [:div.row-fluid
   [:div.alert.alert-success {:class (when (plan-model/trial-over? plan) "alert-error")}
    [:p
     (if (plan-model/trial-over? plan)
       "Your 2-week trial is over!"

       [:span "The " [:strong org-name] " organization has "
        (plan-model/pretty-trial-time plan) " left in its trial."])]
    [:p
     "The trial plan is equivalent to the Solo plan with 6 containers."]
    (when (and (not (:too_many_extensions plan))
               (> 3 (plan-model/days-left-in-trial plan)))
      [:p
       "Need more time to decide? "
       (forms/stateful-button
        [:button.btn.btn-mini.btn-success
         {:data-success-text "Extended!",
          :data-loading-text "Extending...",
          :on-click #(put! controls-ch [:extend-trial {:org-name org-name}])}
         "Extend your trial"])])]])

(defn plans-piggieback-plan-notification [plan current-org-name]
  [:div.row-fluid
   [:div.offset1.span10
    [:div.alert.alert-success
     [:p
      "This organization is covered under " (:org_name plan) "'s plan which has "
      (:containers plan) " containers."]
     [:p
      "If you're an admin in the " (:org_name plan)
      " organization, then you can change plan settings from the "
      [:a {:href (routes/v1-org-settings-subpage {:org (:org_name plan)
                                                  :subpage "plan"})}
       (:org_name plan) " plan page"] "."]
     [:p
      "You can create a separate plan for " current-org-name " by selecting from the plans below."]]]])

(defn plan [data owner]
  (reify
    om/IRender
    (render [_]
      (let [plan (get-in data state/org-plan-path)
            org-name (get-in data state/org-name-path)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html
         (if-not plan
           [:div.loading-spinner common/spinner]

           [:div#billing.plans.pricing.row-fluid
            (when (plan-model/trial? plan)
              (plans-trial-notification plan org-name controls-ch))
            (when (plan-model/piggieback? plan org-name)
              (plans-piggieback-plan-notification plan org-name))
            " + $c(HAML.org_plan_summary()) + $c(HAML.pricing_plans()) + $c(HAML.customers_trust()) + $c(HAML.pricing_features()) + $c(HAML.pricing_faq()) + $c(HAML.confirm_plan_modal())"]))))))

(def main-component
  {:users users
   :projects projects
   :plan plan
   ;; :containers containers
   ;; :organizations organizations
   ;; :billing billing
   ;; :cancel cancel
   })

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
                  (om/build sidebar {:subpage subpage :plan plan :org-name (:name org-data)})
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
