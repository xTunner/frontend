(ns frontend.components.pieces.org-picker
  (:require [frontend.async :refer [raise! navigate!]]
            [frontend.components.common :as common]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.models.user :as user-model]
            [frontend.routes :as routes]
            [frontend.utils :as utils]
            [frontend.utils.bitbucket :as bitbucket]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs :as vcs-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn vcs-github? [item] (contains? #{"github" nil} (:vcs_type item)))
(defn vcs-bitbucket? [item] (= "bitbucket" (:vcs_type item)))

(defn select-vcs-type [vcs-type item]
  (case vcs-type
    "bitbucket" (vcs-bitbucket? item)
    "github"    (vcs-github?    item)))

(defn missing-org-info
  "A message explaining how to enable organizations which have disallowed CircleCI on GitHub."
  [owner]
  [:p
   "Are you missing an organization? You or an admin may need to enable CircleCI for your organization in "
   [:a.gh_app_permissions {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
    "GitHub's application permissions"]
   ". "
   [:a {:on-click #(raise! owner [:refreshed-user-orgs {}]) ;; TODO: spinner while working?
                      :class "active"}
    "Refresh this list"]
   " after you have updated permissions."])

(defn organization [org selected-org owner]
  (let [login (:login org)
        type (if (:org org) :org :user)
        vcs-type (:vcs_type org)
        selected-org-view {:login login :type type :vcs-type vcs-type}]
    [:li.organization {:on-click #(raise! owner [:selected-add-projects-org selected-org-view])
                       :class (when (= selected-org-view selected-org) "active")}
     [:img.avatar {:src (gh-utils/make-avatar-url org :size 50)
            :height 50}]
     [:div.orgname login]
     (if vcs-type
       [:div.org-icon
        [:a {:href (str (case vcs-type
                          "github" (gh-utils/http-endpoint)
                          "bitbucket" (bitbucket/http-endpoint))
                        "/" login)
             :target "_blank"}
         (case vcs-type
           "github" [:i.octicon.octicon-mark-github]
           "bitbucket" [:i.fa.fa-bitbucket])]])]))

(defn org-picker-without-bitbucket [{:keys [orgs user selected-org]} owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IRender
    (render [_]
      (html
       [:div
        [:div.overview
         [:span.big-number "1"]
         [:div.instruction "Choose a GitHub account that you are a member of or have access to."]]
        [:div.organizations
         [:h4 "Your accounts"]
         [:ul.organizations
          (->> orgs
               (filter vcs-github?)
               (map (fn [org] (organization org selected-org owner))))]
         (when (get-in user [:repos-loading :github])
           [:div.orgs-loading
            [:div.loading-spinner common/spinner]])
         (missing-org-info owner)]]))))

(defn org-picker-with-bitbucket [{:keys [orgs user selected-org tab]} owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IRender
    (render [_]
      (let [github-authorized? (user-model/github-authorized? user)
            bitbucket-authorized? (user-model/bitbucket-authorized? user)
            selected-vcs-type (cond
                                tab tab
                                github-authorized? "github"
                                :else "bitbucket")
            github-active? (= "github" selected-vcs-type)
            bitbucket-active? (= "bitbucket" selected-vcs-type)]
        (html
         [:div
          [:div.overview
           [:span.big-number "1"]
           [:div.instruction "Choose an organization that you are a member of."]]
          (om/build tabs/tab-row {:tabs [{:name "github"
                                          :icon (html [:i.octicon.octicon-mark-github])
                                          :label "GitHub"}
                                         {:name "bitbucket"
                                          :icon (html [:i.fa.fa-bitbucket])
                                          :label "Bitbucket"}]
                                  :selected-tab-name selected-vcs-type
                                  :on-tab-click #(navigate! owner (routes/v1-add-projects-path {:_fragment %}))})
          [:div.organizations.card
           (when github-active?
             (if github-authorized?
               (missing-org-info owner)
               [:div
                [:p "GitHub is not connected to your account yet. To connect it, click the button below:"]
                [:a.btn.btn-primary {:href (gh-utils/auth-url)
                                     :on-click #((om/get-shared owner :track-event) {:event-type :authorize-vcs-clicked
                                                                                     :properties {:vcs-type selected-vcs-type}})}
                 "Authorize with GitHub"]]))
           (when (and bitbucket-active?
                      (not bitbucket-authorized?))
             [:div
              [:p "Bitbucket is not connected to your account yet. To connect it, click the button below:"]
              [:a.btn.btn-primary {:href (bitbucket/auth-url)
                                   :on-click #((om/get-shared owner :track-event) {:event-type :authorize-vcs-clicked
                                                                                   :properties {:vcs-type selected-vcs-type}})}
               "Authorize with Bitbucket"]])
           [:ul.organizations
            (->> orgs
                 (filter (partial select-vcs-type selected-vcs-type))
                 (map (fn [org] (organization org selected-org owner))))]
           (when (get-in user [:repos-loading (keyword selected-vcs-type)])
             [:div.orgs-loading
              [:div.loading-spinner common/spinner]])]])))))

(defn picker
  "Shows an org picker. The picker will include a Bitbucket tab if Bitbucket is
  enabled for the given user. Accepts the following params:

  :user         - The user whose orgs we're showing. We'll display that user's
                  :organizations. We'll also decide whether to show a Bitbucket tab based
                  on this user.
  :repos        - The repos the user has access to on the VCS provider. We'll also display
                  the orgs of these repos, even if they're not in the user's
                  :organizations.
  :selected-org - The currently selected org.
  :tab          - (optional) The VCS tab to display (\"github\" or \"bitbucket\").
                  Defaults to \"github\"."
  [{:keys [user selected-org repos tab] :as params} owner]
  (reify
    om/IRender
    (render [_]
      ;; We display you, then all of your organizations, then all of the owners of
      ;; repos that aren't organizations and aren't you. We do it this way because the
      ;; organizations route is much faster than the repos route. We show them
      ;; in this order (rather than e.g. putting the whole thing into a set)
      ;; so that new ones don't jump up in the middle as they're loaded.
      (let [user-org-keys (->> user
                               :organizations
                               (map (juxt :vcs_type :login))
                               set)
            user-org? (comp user-org-keys (juxt :vcs_type :login))
            orgs (concat (sort-by :org (:organizations user))
                         (->> repos
                              (map (fn [{:keys [owner vcs_type]}] (assoc owner :vcs_type vcs_type)))
                              (remove user-org?)
                              distinct))]
        (if (vcs-utils/bitbucket-enabled? user)
          (om/build org-picker-with-bitbucket {:orgs orgs
                                               :user user
                                               :selected-org selected-org
                                               :tab tab})
          (om/build org-picker-without-bitbucket {:orgs orgs
                                                  :user user
                                                  :selected-org selected-org}))))))
