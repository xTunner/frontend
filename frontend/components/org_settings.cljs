(ns frontend.components.org-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.set]
            [frontend.async :refer [put!]]
            [frontend.routes :as routes]
            [frontend.datetime :as datetime]
            [frontend.models.organization :as org-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stripe :as stripe]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.style])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [dommy.macros :refer [node sel sel1]]
                   [frontend.utils :refer [html]]))

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
                                {:page :organizations :text "Organizations"}
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

(defn users [app owner]
  (reify
    om/IRender
    (render [_]
      (let [users (get-in app state/org-users-path)
            projects (get-in app state/org-projects-path)
            org-name (get-in app state/org-name-path)
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

(defn projects [app owner]
  (reify
    om/IRender
    (render [_]
      (let [users (get-in app state/org-users-path)
            projects (get-in app state/org-projects-path)
            {followed-projects true unfollowed-projects false} (group-by #(pos? (count (:followers %)))
                                                                         projects)
            org-name (get-in app state/org-name-path)]
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

(defn plan [app owner]
  (reify

    ;; We're loading Checkout here because the loading status is not something
    ;; that we can hope to serialize into the state. It will be stale when we
    ;; move to a different browser or auto-refresh the page.
    ;; Making the component responsible for loading Checkout seems like the best
    ;; way to make sure it's loaded when we need it.
    om/IInitState
    (init-state [_]
      {:checkout-loaded? (stripe/checkout-loaded?)
       :checkout-loaded-chan (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner [:checkout-loaded-chan])
            checkout-loaded? (om/get-state owner [:checkout-loaded?])]
        (when-not checkout-loaded?
          (go (<! ch) ;; wait for success message
              (utils/mlog "Stripe checkout loaded")
              (om/set-state! owner [:checkout-loaded?] true))
          (utils/mlog "Loading Stripe checkout")
          (stripe/load-checkout ch))))
    om/IWillUnmount
    (will-unmount [_]
      (close! (om/get-state owner [:checkout-loaded-chan])))

    om/IRenderState
    (render-state [_ {:keys [checkout-loaded?]}]
      (let [plan (get-in app state/org-plan-path)
            org-name (get-in app state/org-name-path)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html
         (if-not (and plan checkout-loaded?)
           [:div.loading-spinner common/spinner]

           [:div#billing.plans.pricing.row-fluid
            (when (plan-model/trial? plan)
              (plans-trial-notification plan org-name controls-ch))
            (when (plan-model/piggieback? plan org-name)
              (plans-piggieback-plan-notification plan org-name))
            (om/build plans-component/plans app)
            (shared/customers-trust)
            plans-component/pricing-features
            plans-component/pricing-faq]))))))

(defn containers [app owner]
  (reify
    om/IRender
    (render [_]
      (let [plan (get-in app state/org-plan-path)
            selected-containers (or (get-in app state/selected-containers-path)
                                    (:containers plan))
            controls-ch (om/get-shared owner [:comms :controls])
            old-total (plan-model/stripe-cost plan)
            new-total (plan-model/cost (:template_properties plan) selected-containers)]
        (html
         (if-not plan
           [:div.loading-spinner common/spinner]

           [:div#edit-plan
            [:fieldset
             [:legend
              "Our pricing is flexible and scales with you. Add as many containers as you want for $"
              (get-in plan [:template_properties :container_cost])
              "/month each."]
             [:div.main-content
              [:div.left-section
               [:div.plan
                [:h2 "Your Current Plan"]
                [:p
                 [:strong "$" old-total] "/ month"]
                [:ul [:li "Includes " (:containers plan) " containers"]
                 [:li "Additional containers for $"
                  (get-in plan [:template_properties :container_cost]) "/month"]
                 [:li [:strong "No other limits"]]]]]
              [:div.right-section
               [:h3 "New total: $" new-total]
               [:h4
                "Old total: $" old-total
                (when (plan-model/grandfathered? plan)
                  [:span.grandfather
                   "(grandfathered"
                   [:i.fa.fa-question-circle
                    {:title: "We've changed plan prices since you signed up, so you're grandfathered in at the old price!"
                     :data-bind "tooltip: {animation: false}"}]
                   ")"])]
               [:form
                [:div.container-picker
                 [:p "You can add or remove containers below; more containers means faster builds and lower queue times."]
                 [:div.container-slider
                  [:span (get-in plan [:template_properties :free_containers])]
                  (let [max (if (< selected-containers 80)
                              80
                              (let [num (+ 80 selected-containers)]
                                (+ num (- 10 (mod num 10)))))]
                    (list
                     [:input#rangevalue
                      {:type "range"
                       :value selected-containers
                       :min (get-in plan [:template_properties :free_containers])
                       :max max
                       :on-change #(utils/edit-input controls-ch state/selected-containers-path %
                                                     :value (int (.. % -target -value)))}]
                     [:span max]))]
                 [:div.container-input
                  [:input
                   {:type "text"
                    :value selected-containers
                    :on-change #(utils/edit-input controls-ch state/selected-containers-path %
                                                  :value (int (.. % -target -value)))}]]]
                [:fieldset
                 (forms/managed-button
                  [:button.btn.btn-large.btn-primary.center
                   {:data-success-text "Saved",
                    :data-loading-text "Saving...",
                    :type "submit"
                    :on-click #(do (put! controls-ch [:update-containers-clicked {:containers selected-containers}])
                                   false)}
                   "Update plan"])
                 (when (< old-total new-total)
                   [:span.help-block
                    "We'll charge your card today, for the prorated difference between your new and old plans."])
                 (when (> old-total new-total)
                   [:span.help-block
                    "We'll credit your account, for the prorated difference between your new and old plans."])]]]]
             plans-component/pricing-faq]]))))))

(defn organizations [app owner]
  (om/component
   (html
    (let [org-name (get-in app state/org-name-path)
          user-login (:login (get-in app state/user-path))
          user-orgs (get-in app state/user-organizations-path)
          plan (get-in app state/org-plan-path)
          elligible-piggyback-orgs (-> (map :login user-orgs)
                                       (set)
                                       (disj org-name)
                                       (conj user-login)
                                       (sort))
          ;; This lets users toggle selected piggyback orgs that are already in the plan. Merges:
          ;; (:piggieback_orgs plan): ["org-a" "org-b"] with
          ;; selected-orgs:           {"org-a" false "org-c" true}
          ;; to return #{"org-b" "org-c"}
          selected-piggyback-orgs (set (keys (filter last
                                                     (merge (zipmap (:piggieback_orgs plan) (repeat true))
                                                            (get-in app state/selected-piggyback-orgs-path)))))
          controls-ch (om/get-shared owner [:comms :controls])]
      [:div.row-fluid
       [:div.span8
        [:fieldset
         [:legend "Extra organizations"]
         [:p
          "Your plan covers all repositories (including forks) in the "
          [:strong org-name]
          " organization by default."]
         [:p "You can let any GitHub organization you belong to, including personal accounts, piggyback on your plan. Projects in your piggyback organizations will be able to run builds on your plan."]
         [:p
          [:span.label.label-info "Note:"]
          " Members of the piggyback organizations will be able to see that you're paying for them, the name of your plan, and the number of containers you've paid for. They won't be able to edit the plan unless they are also admins on the " org-name " org."]
         (if-not user-orgs
           [:div "Loading organization list..."]
           [:div.row-fluid
            [:div.span12
             [:form
              [:div.controls
               (for [org elligible-piggyback-orgs]
                 [:div.control
                  [:label.checkbox
                   [:input
                    {:value org
                     :checked (contains? selected-piggyback-orgs org)
                     ;; Note: this is broken if the org is already in piggieback_orgs, need to explicitly pass the value :(
                     :on-change #(utils/toggle-input controls-ch (conj state/selected-piggyback-orgs-path org) %)
                     :type "checkbox"}]
                   org]])]
              [:div.form-actions.span7
               (forms/managed-button
                [:button.btn.btn-large.btn-primary
                 {:data-success-text "Saved",
                  :data-loading-text "Saving...",
                  :type "submit",
                  :on-click #(do (put! controls-ch [:save-piggyback-orgs-clicked {:org-name org-name
                                                                                  :selected-piggyback-orgs selected-piggyback-orgs}])
                                 false)
                  :data-bind "click: saveOrganizations"}
                 "Also pay for these organizations"])]]]])]]]))))

(def main-component
  {:users users
   :projects projects
   :plan plan
   :containers containers
   :organizations organizations
   ;; :billing billing
   ;; :cancel cancel
   })

(defn org-settings [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (get app :org-settings-subpage)
            org-data (get-in app state/org-data-path)
            plan (get-in app state/org-plan-path)]
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
                       (om/build (get main-component subpage projects) app)
                       [:div (om/build non-admin-plan
                                       {:login (get-in app [:current-user :login])
                                        :org-name (:org-settings-org-name app)
                                        :subpage subpage})])]]]])])))))
