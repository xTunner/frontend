(ns frontend.components.project-settings
  (:require [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs.core.async :as async :refer [<! >! alts! chan close! sliding-buffer]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.components.account :as account]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.inputs :as inputs]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.table :as table]
            [frontend.components.project.common :as project-common]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.models.user :as user-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.bitbucket :as bb-utils]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.html :refer [hiccup->html-str open-ext]]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.crypt.base64 :as base64]
            [goog.string :as gstring]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn branch-names [project-data]
  (map (comp gstring/urlDecode name) (keys (:branches (:project project-data)))))

(defn branch-picker [project-data owner opts]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/typeahead
       "#branch-picker-typeahead-hack"
       {:source (branch-names project-data)
        :updater (fn [branch]
                   (raise! owner [:edited-input {:path (conj state/inputs-path :settings-branch) :value branch}])
                   branch)}))
    om/IRender
    (render [_]
      (let [{:keys [button-text channel-message channel-args]
             :or {button-text "Start a build" channel-message :started-edit-settings-build}} opts
             project (:project project-data)
             project-id (project-model/id project)
             default-branch (:default_branch project)
             settings-branch (get (inputs/get-inputs-from-app-state owner) :settings-branch default-branch)]
        (html
         [:form
          [:input {:name "branch"
                   :id "branch-picker-typeahead-hack"
                   :required true
                   :type "text"
                   :value (str settings-branch)
                   :on-change #(utils/edit-input owner (conj state/inputs-path :settings-branch) %)}]
          [:label {:placeholder "Test settings on..."}]
          (forms/managed-button
           [:input
            {:value button-text
             :on-click #(raise! owner [channel-message (merge {:project-id project-id} channel-args)])
             :data-loading-text "Starting..."
             :data-success-text "Started..."
             :type "submit"}])])))))

(defn follow-sidebar [project owner]
  (reify
    om/IRender
    (render [_]
      (let [project-id (project-model/id project)
            vcs-url (:vcs_url project)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html
         [:article
          [:div
           (if (:followed project)
             (list
              [:h2 "You're following " (vcs-url/project-name vcs-url)]
              [:p
               "We'll keep an eye on this and update you with personalized build emails. "
               "You can stop these any time from your "
               [:a {:href "/account"} "account settings"]
               "."]
              (forms/managed-button
                [:button.btn.btn-warning {:on-click #(raise! owner [:unfollowed-project {:vcs-url vcs-url
                                                                                         :project-id project-id}])
                                          :data-loading-text "Unfollowing..."}
                 "Unfollow"])
               " "
               (forms/managed-button
                 [:button.btn.btn-danger {:on-click #(raise! owner [:stopped-building-project {:vcs-url vcs-url
                                                                                               :project-id project-id}])
                                          :data-loading-text "Stopping Builds..."
                                          :data-success-text "Builds Stopped"}
                  "Stop Building on CircleCI"]))
             (list
              [:h2 "You're not following this repo"]
              [:p
               "We can't update you with personalized build emails unless you follow this project. "
               "Projects are only tested if they have a follower."]
              (forms/managed-button
               [:button {:on-click #(raise! owner [:followed-project {:vcs-url vcs-url
                                                                      :project-id project-id}])
                         :data-loading-text "Following..."}
                "Follow"])))]])))))

(defn overview [project-data owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:section.overview
        [:article
         [:h2 "How to configure " (vcs-url/project-name (get-in project-data [:project :vcs_url]))]
         [:h4 "Option 1"]
         [:p "Do nothing! CircleCI infers many settings automatically. Works great for Ruby, Python, NodeJS, Java and Clojure. However, if it needs tweaks or doesn't work, see below."]
         [:h4 "Option 2"]
         [:p
          "Override inferred settings and add new test commands "
          [:a {:href "#setup"} "through the web UI"]
          ". This works great for prototyping changes."]
         [:h4 "Option 3"]
         [:p
          "Override all settings via a "
          [:a (open-ext {:href "https://circleci.com/docs/configuration/"}) "circle.yml file"]
          " in your repo. Very powerful."]]
        (om/build follow-sidebar (:project project-data))]))))

(defn build-environment [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            plan (:plan project-data)
            project-id (project-model/id project)
            project-name (vcs-url/project-name (:vcs_url project))
            ;; This project's feature flags
            feature-flags (project-model/feature-flags project)
            describe-flag (fn [{:keys [flag title blurb]}]
                            (when (contains? (set (keys feature-flags)) flag)
                              [:li
                               [:h4 title]
                               [:p blurb]
                               [:form
                                [:ul
                                 [:li.radio
                                  [:label
                                   [:input
                                    {:type "radio"
                                     :checked (get feature-flags flag)
                                     :on-change #(raise! owner [:project-feature-flag-checked {:project-id project-id
                                                                                               :flag flag
                                                                                               :value true}])}]
                                   " On"]]
                                 [:li.radio
                                  [:label
                                   [:input
                                    {:type "radio"
                                     :checked (not (get feature-flags flag))
                                     :on-change #(raise! owner [:project-feature-flag-checked {:project-id project-id
                                                                                               :flag flag
                                                                                               :value false}])}]
                                   " Off"]]]]]))]
        (html
          [:section
           [:article
            [:h2 "Build Environment"]
            [:ul
             (describe-flag {:flag :osx
                             :title "Build OS X project"
                             :blurb [:div
                                     [:p
                                      [:strong
                                       "Note: this option only works if the project builds under an OS X plan. "
                                       "Configure that option "
                                       [:a {:href (routes/v1-org-settings-path {:org (:org_name plan)
                                                                                :vcs_type (:vcs_type plan)
                                                                                :_fragment "osx-pricing"})} "here"]
                                       "."]]
                                     [:p
                                      "This option reflects whether CircleCI will run builds for this project "
                                      "against Linux-based hardware or OS X-based hardware. Please use this "
                                      "setting as an override if we have incorrectly inferred where this build should run."]]})
             (when-not (config/enterprise?)
               [:li
                [:h4 "OS to use for builds"]
                [:p [:p
                     "Select the operating system in which to run your Linux builds."
                     [:p [:strong "Please note that you need to trigger a build by pushing commits to GitHub (instead of rebuilding) to apply the new setting."]]]]
                [:form
                 [:ul
                  [:li.radio
                   [:label
                    [:input
                     {:type "radio"
                      :checked (not (get feature-flags :trusty-beta))
                      :on-change #(raise! owner [:project-feature-flag-checked {:project-id project-id
                                                                                :flag :trusty-beta
                                                                                :value false}])}]
                    " Ubuntu 12.04 (Precise)"]]
                  [:li.radio
                   [:label
                    [:input
                     {:type "radio"
                      :checked (get feature-flags :trusty-beta)
                      :on-change #(raise! owner [:project-feature-flag-checked {:project-id project-id
                                                                                :flag :trusty-beta
                                                                                :value true}])}]
                    " Ubuntu 14.04 (Trusty)"]]]]])]]])))))

(defn parallel-label-classes [{:keys [plan project] :as project-data} parallelism]
  (concat
   []
   (when (and (> parallelism 1) (project-model/osx? project)) ["disabled"])
   (when (> parallelism (project-model/buildable-parallelism plan project)) ["disabled"])
   (when (= parallelism (get-in project-data [:project :parallel])) ["selected"])
   (when (not= 0 (mod (project-model/usable-containers plan project) parallelism)) ["bad_choice"])))

(defn parallelism-tile
  "Determines what we show when they hover over the parallelism option"
  [project-data owner parallelism]
  (let [project (:project project-data)
        {{plan-org-name :name
          plan-vcs-type :vcs_type} :org
         :as plan}
        (:plan project-data)
        project-id (project-model/id project)]
    (list
     [:div.parallelism-upgrades
      (if-not (plan-model/in-trial? plan)
        (cond (and (project-model/osx? project)
                   (> parallelism 1))
              ;; OS X projects should not use parallelism. We don't have the
              ;; ability to parallelise XCode tests yet and have a limited
              ;; number of available OSX VMs. Setting parallelism for OS X
              ;; wastes VMs, reducing the number of builds we can run.
              [:div.insufficient-plan
               "OS X projects are currently limited to 1x parallelism."]

              (> parallelism (plan-model/max-parallelism plan))
              [:div.insufficient-plan
               "Your plan only allows up to "
               (plan-model/max-parallelism plan) "x parallelism."
               [:a (common/contact-support-a-info owner)
                "Contact us if you'd like more."]]

              (> parallelism (project-model/buildable-parallelism plan project))
              [:div.insufficient-containers
               "Not enough containers for " parallelism "x."
               [:a {:href (routes/v1-org-settings-path {:org plan-org-name
                                                        :vcs_type plan-vcs-type
                                                        :_fragment "linux-pricing"})
                    :on-click #((om/get-shared owner :track-event) {:event-type :add-more-containers-clicked})}
                "Add More"]])
        (when (> parallelism (project-model/buildable-parallelism plan project))
          [:div.insufficient-trial
           "Trials only come with " (plan-model/trial-containers plan) " available containers."
           [:a {:href (routes/v1-org-settings-path {:org plan-org-name
                                                    :vcs_type plan-vcs-type
                                                    :_fragment "linux-pricing"})}
            "Add a plan"]]))]

     ;; Tell them to upgrade when they're using more parallelism than their plan allows,
     ;; but only on the tiles between (allowed parallelism and their current parallelism]
     (when (and (> (:parallel project) (project-model/usable-containers plan project))
                (>= (:parallel project) parallelism)
                (> parallelism (project-model/usable-containers plan project)))
       [:div.insufficient-minimum
        "Unsupported. Upgrade or lower parallelism."
        [:i.fa.fa-question-circle {:title (str "You need " parallelism " containers on your plan to use "
                                               parallelism "x parallelism.")}]
        [:a {:href (routes/v1-org-settings-path {:org plan-org-name
                                                 :vcs_type plan-vcs-type
                                                 :_fragment "linux-pricing"})}
         "Upgrade"]]))))

(defn parallelism-picker [project-data owner]
  [:div.parallelism-picker
   (if-not (:plan project-data)
     [:div.loading-spinner common/spinner]
     (let [plan (:plan project-data)
           project (:project project-data)
           project-id (project-model/id project)]
       (list
        (when (:parallelism-edited project-data)
          [:div.try-out-build
           (om/build branch-picker project-data {:opts {:button-text (str "Try a build!")}})])
        [:form.parallelism-items
         (for [parallelism (range 1 (inc (max 24 (plan-model/max-parallelism plan))))]
           [:label {:class (parallel-label-classes project-data parallelism)
                    :for (str "parallel_input_" parallelism)}
            parallelism
            (parallelism-tile project-data owner parallelism)
            [:input {:id (str "parallel_input_" parallelism)
                     :type "radio"
                     :name "parallel"
                     :value parallelism
                     :on-click #(raise! owner [:selected-project-parallelism
                                               {:project-id project-id
                                                :parallelism parallelism}])
                     :disabled (> parallelism (project-model/buildable-parallelism plan project))
                     :checked (= parallelism (:parallel project))}]])])))])

(defn trial-activation-banner [data owner]
  (reify
    om/IRender
    (render [_]
      (let [org (get-in data state/project-plan-org-name-path)
            days-left (-> (get-in data state/project-plan-path)
                          :trial_end
                          (time-format/parse)
                          (->> (time/interval (time/now)))
                          (time/in-days)
                          (+ 1))
            days-left-str (str days-left (if (> days-left 1)
                                           " days"
                                           " day"))]
        (html
          [:div.alert.offer
           [:div.text
            [:span org"'s plan has "days-left-str" left on its trial. You may want to try increasing parallelism below to get the most value out of your containers."]]])))))

(defn parallel-builds [data owner]
  (reify
    om/IRender
    (render [_]
      (let [project-data (get-in data state/project-data-path)
            plan (get-in data state/project-plan-path)]
        (html
          [:section
           (when (plan-model/in-trial? plan)
             (om/build trial-activation-banner data))
           [:article
            [:h2 (str "Change parallelism for " (vcs-url/project-name (get-in project-data [:project :vcs_url])))]
            (if-not (:plan project-data)
              [:div.loading-spinner common/spinner]
              (list (parallelism-picker project-data owner)
                    (project-common/mini-parallelism-faq project-data)))]])))))

(defn result-box
  [{:keys [success? message result-path]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.flash-error-wrapper.row-fluid
         [:div.offset1.span10
          [:div.alert.alert-block {:class (if success?
                                            "alert-success"
                                            "alert-danger")}
           [:a.close {:on-click #(raise! owner [:dismiss-result result-path])}
            "x"]
           message]]]))))

(defn clear-cache-button
  [cache-type project-data owner]
  (forms/managed-button
    [:button.btn.btn-primary
     {:data-loading-text "Clearing cache..."
      :data-success-text "Cleared"
      :data-failure-text "Clearing failed"
      :on-click #(raise! owner
                         [:clear-cache
                          {:type cache-type
                           :project-id (-> project-data
                                           :project
                                           project-model/id)}])}
     (case cache-type
       "build" "Clear Dependency Cache"
       "source" "Clear Source Cache")]))

(defn clear-caches
  [project-data owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:section
         [:div.card.detailed
          [:h4 "Dependency Cache"]
          [:div.details
           (when-let [res (-> project-data :build-cache-clear)]
             (om/build result-box
                       (assoc res
                              :result-path
                              (conj state/project-data-path :build-cache-clear))))
           [:p "CircleCI saves a copy of your dependencies to prevent downloading them all on each build."]
           [:p [:b "NOTE: "] "Clearing your dependency cache will cause the next build to completely recreate dependencies. In some cases this can add considerable time to that build.  Also, you may need to ensure that you have no running builds when using this to prevent the old cache from being resaved."]
           [:hr]
           (clear-cache-button "build" project-data owner)]]
         [:div.card.detailed
          [:h4 "Source Cache"]
          [:div.details
           (when-let [res (-> project-data :source-cache-clear)]
             (om/build result-box
                       (assoc res
                              :result-path
                              (conj state/project-data-path :source-cache-clear))))
           [:p "CircleCI saves a copy of your source code on our system and pulls only changes since the last build on each branch."]
           [:p [:b "NOTE: "] "Clearing your source cache will cause the next build to download a fresh copy of your source. In some cases this can add considerable time to that build.  Doing this too frequently or when you have a large number of high parallelism builds also carries some risk of becoming rate-limited by GitHub, which will prevent your builds from being able to download source until the rate-limit expires.  Also, you may need to ensure that you have no running builds when using this to prevent the old cache from being resaved."]
           [:hr]
           (clear-cache-button "source" project-data owner)]]]))))

(defn env-vars [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            inputs (inputs/get-inputs-from-app-state owner)
            new-env-var-name (:new-env-var-name inputs)
            new-env-var-value (:new-env-var-value inputs)
            project-id (project-model/id project)]
        (html
          [:section
           [:article
            [:h2 "Environment Variables for " (vcs-url/project-name (:vcs_url project))]
            [:div
             [:p
              "Add environment variables to the project build.  You can add sensitive data (e.g. API keys) here, rather than placing them in the repository. "
              "The values can be any bash expression and can reference other variables, such as setting "
              [:code "M2_MAVEN"] " to " [:code "${HOME}/.m2)"] "."]

             [:p
              " To disable string substitution you need to escape the " [:code "$"]
              " characters by prefixing them with " [:code "\\"] "."
              " For example, a value like " [:code "usd$"] " would be entered as " [:code "usd\\$"] "." ]
             [:form
              [:div.form-group
               [:label "Name"]
               [:input.form-control#env-var-name
                {:required true, :type "text", :value new-env-var-name
                 :on-change #(utils/edit-input owner (conj state/inputs-path :new-env-var-name) %)}]]
              [:div.form-group
               [:label "Value"]
               [:input.form-control#env-var-value
                {:required true
                 :type "text"
                 :value new-env-var-value
                 :auto-complete "off"
                 :on-change #(utils/edit-input owner (conj state/inputs-path :new-env-var-value) %)}]]

              [:div.form-group
               (forms/managed-button
                 [:input.btn.btn-primary {:data-failed-text "Failed",
                                          :data-success-text "Added",
                                          :data-loading-text "Adding...",
                                          :value "Add Variable",
                                          :type "submit"
                                          :on-click #(raise! owner [:created-env-var {:project-id project-id}])}])]]
             (when-let [env-vars (seq (:envvars project-data))]
               (om/build table/table
                         {:rows env-vars
                          :columns [{:header "Name"
                                     :cell-fn :name}
                                    {:header "Value"
                                     :cell-fn :value}
                                    {:header "Remove"
                                     :type #{:shrink :right}
                                     :cell-fn
                                     (fn [env-var]
                                       (table/action-button
                                        #(raise! owner [:deleted-env-var {:project-id project-id
                                                                          :env-var-name (:name env-var)}])
                                        (icon/delete)))}]}))]]])))))

(defn advance [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            project-id (project-model/id project)
            project-name (vcs-url/project-name (:vcs_url project))
            ;; This project's feature flags
            feature-flags (project-model/feature-flags project)
            describe-flag (fn [{:keys [flag title blurb]}]
                            (when (contains? (set (keys feature-flags)) flag)
                              [:li
                               [:h4 title]
                               [:p blurb]
                               [:form
                                [:ul
                                 [:li.radio
                                  [:label
                                   [:input
                                    {:type "radio"
                                     :checked (get feature-flags flag)
                                     :on-change #(raise! owner [:project-feature-flag-checked {:project-id project-id
                                                                                               :flag flag
                                                                                               :value true}])}]
                                   " On"]]
                                 [:li.radio
                                  [:label
                                   [:input
                                    {:type "radio"
                                     :checked (not (get feature-flags flag))
                                     :on-change #(raise! owner [:project-feature-flag-checked {:project-id project-id
                                                                                               :flag flag
                                                                                               :value false}])}]
                                   " Off"]]]]]))]
        (html
         [:section
          [:article
           [:h2 "Advanced Settings"]
           [:ul
            (describe-flag {:flag :junit
                            :title "JUnit support"
                            :blurb [:p
                                    "This flag enables collection of test data via junit xml or cucumber json files,"
                                    " which we use to better display test results and make parallel builds more"
                                    " efficient.  It also adds the necessary flags for collecting this to automatically"
                                    " inferred ruby or python test commands, though for RSpec of Minitest you'll need"
                                    " to add the necessary formatter gem - see "
                                    [:a (open-ext {:href "https://circleci.com/docs/test-metadata/#metadata-collection-in-custom-test-steps"})
                                     "the docs"] " for more information."
                                    ]})
            (describe-flag {:flag :set-github-status
                            :title "GitHub Status updates"
                            :blurb [:p
                                    "By default, we update the status of every pushed commit with "
                                    "GitHub's status API. If you'd like to turn this off (if, for example, "
                                    "this is conflicting with another service), you can do so below."]})
            (describe-flag {:flag :oss
                            :title "Free and Open Source"
                            :blurb [:p
                                   "Organizations have three free containers "
                                    "reserved for F/OSS projects; enabling this will allow this project's "
                                    "builds to use them and let others see your builds, both through the "
                                    "web UI and the API."]})
            (describe-flag {:flag :build-fork-prs
                            :title "Permissive building of fork pull requests"
                            :blurb (list
                                    [:p
                                     "Run builds of fork pull request changes with this project's configuration. "
                                     "CircleCI will automatically update the commit status shown on GitHub's "
                                     "pull request page."]
                                    [:p
                                     "There are serious security concerns with this setting (see "
                                     [:a (open-ext {:href "https://circleci.com/docs/fork-pr-builds/"}) "the documentation"] " for details.) "
                                     "If you have SSH keys, sensitive env vars or AWS credentials stored in your project settings and "
                                     "untrusted forks can make pull requests against your repo, then this option "
                                     "isn't for you!"])})
            (describe-flag {:flag :osx-code-signing-enabled
                            :title "Code Signing Support"
                            :blurb [:p
                                    "Enable automatic importing of code-signing identities and provisioning "
                                    "profiles into the system keychain to simplify the code-signing process."]})]]])))))

(defn dependencies [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            project-id (project-model/id project)
            inputs (inputs/get-inputs-from-app-state owner)
            settings (state-utils/merge-inputs project inputs [:setup :dependencies :post_dependencies])]
        (html
         [:section.dependencies-page
          [:article
           [:h2 "Install dependencies for " (vcs-url/project-name (:vcs_url project))]
           [:p
            "You can also set your dependencies commands from your "
            [:a (open-ext {:href "https://circleci.com/docs/configuration/#dependencies"}) "circle.yml"] ". "
            "Note that anyone who can see this project on GitHub will be able to see these in your build pages. "
            "Don't put any secrets here that you wouldn't check in! Use our "
            [:a {:href "#env-vars"} "environment variables settings page"]
            " instead."]
           [:div.dependencies-inner
            [:form.spec_form
             [:fieldset
              [:div.form-group
               [:label "Pre-dependency commands"]
               [:textarea.dumb.form-control {:name "setup",
                                             :required true
                                             :value (str (:setup settings))
                                             :on-change #(utils/edit-input owner (conj state/inputs-path :setup) % owner)}]
              [:p "Run extra commands before the normal setup, these run before our inferred commands. All commands are arbitrary bash statements, and run on Ubuntu 12.04. Use this to install and setup unusual services, such as specific DNS provisions, connections to a private services, etc."]]
              [:div.form-group
               [:label "Dependency overrides"]
               [:textarea.dumb.form-control {:name "dependencies",
                                             :required true
                                             :value (str (:dependencies settings))
                                             :on-change #(utils/edit-input owner (conj state/inputs-path :dependencies) %)}]
               [:p "Replace our inferred setup commands with your own bash commands. Dependency overrides run instead of our inferred commands for dependency installation. If our inferred commands are not to your liking, replace them here. Use this to override the specific pre-test commands we run, such as "
                [:code "bundle install"] ", " [:code "rvm use"] ", " [:code "ant build"] ", "
                [:code "configure"] ", " [:code "make"] ", etc."]]
              [:div.form-group
               [:label "Post-dependency commands"]
               [:textarea.dumb.form-control {:required true
                                             :value (str (:post_dependencies settings))
                                             :on-change #(utils/edit-input owner (conj state/inputs-path :post_dependencies) %)}]
               [:p "Run extra commands after the normal setup, these run after our inferred commands for dependency installation. Use this to run commands that rely on the installed dependencies."]]
              (forms/managed-button
               [:input.btn.btn-primary {:value "Next, set up your tests",
                                        :type "submit"
                                        :data-loading-text "Saving..."
                                        :on-click #(raise! owner [:saved-dependencies-commands {:project-id project-id}])}])]]]]])))))

(defn tests [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            project-id (project-model/id project)
            inputs (inputs/get-inputs-from-app-state owner)
            settings (state-utils/merge-inputs project inputs [:test :extra])]
        (html
         [:section.tests-page
          [:article
           [:h2 "Set up tests for " (vcs-url/project-name (:vcs_url project))]
           [:p
            "You can also set your test commands from your "
            [:a (open-ext {:href "https://circleci.com/docs/configuration/#dependencies"}) "circle.yml"] ". "
            "Note that anyone who can see this project on GitHub will be able to see these in your build pages. "
            "Don't put any secrets here that you wouldn't check in! Use our "
            [:a {:href "#env-vars"} "environment variables settings page"]
            " instead."]
           [:div.tests-inner
            [:fieldset.spec_form
             [:div.form-group
              [:label "Test commands"]
              [:textarea.dumb.form-control {:name "test",
                                            :required true
                                            :value (str (:test settings))
                                            :on-change #(utils/edit-input owner (conj state/inputs-path :test) %)}]
              [:p "Replace our inferred test commands with your own inferred commands. These test commands run instead of our inferred test commands. If our inferred commands are not to your liking, replace them here. As usual, all commands are arbitrary bash, and run on Ubuntu 12.04."]]
             [:div.form-group
              [:label "Post-test commands"]
              [:textarea.dumb.form-control {:name "extra",
                                            :required true
                                            :value (str (:extra settings))
                                            :on-change #(utils/edit-input owner (conj state/inputs-path :extra) %)}]
              [:p "Run extra test commands after the others finish. Extra test commands run after our inferred commands. Add extra tests that we haven't thought of yet."]]

             (forms/managed-button
              [:input.btn.btn-primary {:name "save",
                                       :data-loading-text "Saving...",
                                       :value "Save commands",
                                       :type "submit"
                                       :on-click #(raise! owner [:saved-test-commands {:project-id project-id}])}])
             [:div.try-out-build
              (om/build branch-picker
                        project-data
                        {:opts {:button-text "Save & Go!"
                                :channel-message :saved-test-commands
                                :channel-args {:project-id project-id :start-build? true}}})]]]]])))))

(defn fixed-failed-input [{:keys [settings field]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/tooltip (str "#fixed-failed-input-tooltip-hack-" (string/replace (name field) "_" "-"))))
    om/IRender
    (render [_]
      (html
       (let [notify_pref (get settings field)
             id (string/replace (name field) "_" "-")]
         [:label {:for id}
          [:input {:id id
                   :checked (= "smart" notify_pref)
                   ;; note: can't use inputs-state here because react won't let us
                   ;;       change checked state without rerendering
                   :on-change #(utils/edit-input owner (conj state/project-path field) %
                                                 :value (if (= "smart" notify_pref) nil "smart"))
                   :value "smart"
                   :type "checkbox"}]
          [:span "Fixed/Failed Only"]
          [:i.fa.fa-question-circle {:id (str "fixed-failed-input-tooltip-hack-" id)
                                     :title "Only send notifications for builds that fail or fix the tests and the first build on a new branch. Otherwise, send a notification for every build."}]])))))

(defn chatroom-item [project-id settings owner
                     {:keys [service doc inputs show-fixed-failed? top-section-content settings-keys]}]
  (let [service-id (string/lower-case service)]
    [:div {:class (str "chat-room-item " service-id)}
     [:div.chat-room-head [:h4 {:class (str "chat-i-" service-id)} service]]
     [:div.chat-room-body
      [:section
       doc
       top-section-content
       (when show-fixed-failed?
         (om/build fixed-failed-input {:settings settings :field (keyword (str service-id "_notify_prefs"))}))]
      [:section
       (for [{:keys [field placeholder] :as input} inputs
             :when input]
         (list
          [:input {:id (string/replace (name field) "_" "-") :required true :type "text"
                   :value (str (get settings field))
                   :on-change #(utils/edit-input owner (conj state/inputs-path field) %)}]
          [:label {:placeholder placeholder}]))
       (let [event-data {:project-id project-id :merge-paths (map vector settings-keys)}]
         [:div.chat-room-buttons
          (forms/managed-button
           [:button.save {:on-click #(raise! owner [:saved-project-settings event-data])
                          :data-loading-text "Saving"
                          :data-success-text "Saved"}
            "Save"])
          (forms/managed-button
           [:button.test {:on-click #(raise! owner [:test-hook (assoc event-data :service service-id)])
                          :data-loading-text "Testing"
                          :data-success-text "Tested"}
            "& Test Hook"])])]]]))

(defn webhooks [project-data owner]
  (om/component
   (html
    [:section
     [:article
      [:h2 "Webhooks"]
      [:div.doc
       [:p
        "CircleCI also supports webhooks, which run at the end of a build. They can be configured in your "
        [:a (open-ext {:href "https://circleci.com/docs/configuration#notify" :target "_blank"})
         "circle.yml file"]
        "."]]]])))

(defn notifications [project-data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-slack-channel-override (-> project-data :project :slack_channel_override seq nil? not)})
    om/IDidMount
    (did-mount [_]
      (utils/equalize-size (om/get-node owner) "chat-room-item")
      (utils/tooltip "#slack-channel-override"))
    om/IDidUpdate
    (did-update [_ _ _]
      (utils/equalize-size (om/get-node owner) "chat-room-item"))
    om/IRenderState
    (render-state [_ state]
      (let [project (:project project-data)
            project-id (project-model/id project)
            inputs (inputs/get-inputs-from-app-state owner)
            settings (state-utils/merge-inputs project inputs project-model/notification-keys)]
        (html
         [:section
          [:article
           [:h2 "Chatroom Integrations"]
           [:p "If you want to control chat notifications on a per branch basis, "
            [:a (open-ext {:href "https://circleci.com/docs/configuration#per-branch-notifications"}) "see our documentation"] "."]
           [:div.chat-rooms
            (for [chat-spec [{:service "Slack"
                              :doc (list [:p "To get your Webhook URL, visit Slack's "
                                          [:a {:href "https://my.slack.com/services/new/circleci"}
                                           "CircleCI Integration"]
                                          " page, choose a default channel, and click the green \"Add CircleCI Integration\" button at the bottom of the page."]
                                         [:div
                                          [:label
                                           [:input
                                            {:type "checkbox"
                                             :checked (:show-slack-channel-override state)
                                             :on-change #(do (om/update-state! owner :show-slack-channel-override not)
                                                             (utils/edit-input owner (conj state/project-path :slack_channel_override) %
                                                                               :value ""))}
                                            [:span "Override room"]
                                            [:i.fa.fa-question-circle {:id "slack-channel-override"
                                                                       :title "If you want to send notifications to a different channel than the webhook URL was created for, enter the channel ID or channel name below."}]]]])
                              :inputs [{:field :slack_webhook_url :placeholder "Webhook URL"}
                                       (when (:show-slack-channel-override state)
                                         {:field :slack_channel_override :placeholder "Room"})]
                              :show-fixed-failed? true
                              :settings-keys project-model/slack-keys}

                             {:service "Hipchat"
                              :doc (list [:p "To get your API token, create a \"notification\" token via the "
                                          [:a {:href "https://hipchat.com/admin/api"} "HipChat site"] "."]
                                         [:div
                                          [:label ;; hipchat is a special flower
                                           {:for "hipchat-notify"}
                                           [:input#hipchat-notify
                                            {:type "checkbox"
                                             :checked (:hipchat_notify settings)
                                             ;; n.b. can't use inputs-state b/c react won't changed
                                             ;;      checked state without a rerender
                                             :on-change #(utils/edit-input owner (conj state/project-path :hipchat_notify) %
                                                                           :value (not (:hipchat_notify settings)))}]
                                           [:span "Show popups"]]])
                              :inputs [{:field :hipchat_room :placeholder "Room"}
                                       {:field :hipchat_api_token :placeholder "API"}]
                              :show-fixed-failed? true
                              :settings-keys project-model/hipchat-keys}

                             {:service "Flowdock"
                              :doc [:p "To get your API token, visit your Flowdock, then click the \"Settings\" icon on the left. On the settings tab, click \"Team Inbox\""]
                              :inputs [{:field :flowdock_api_token :placeholder "API"}]
                              :show-fixed-failed? false
                              :settings-keys project-model/flowdock-keys}

                             {:service "Campfire"
                              :doc [:p "To get your API token, visit your company Campfire, then click \"My info\". Note that if you use your personal API token, campfire won't show the notifications to you!"]
                              :inputs [{:field :campfire_room :placeholder "Room"}
                                       {:field :campfire_subdomain :placeholder "Subdomain"}
                                       {:field :campfire_token :placeholder "API"}]
                              :show-fixed-failed? true
                              :settings-keys project-model/campfire-keys}

                             {:service "IRC"
                              :doc nil
                              :inputs [{:field :irc_server :placeholder "Hostname"}
                                       {:field :irc_channel :placeholder "Channel"}
                                       {:field :irc_keyword :placeholder "Private Keyword"}
                                       {:field :irc_username :placeholder "Username"}
                                       {:field :irc_password :placeholder "Password (optional)"}]
                              :show-fixed-failed? true
                              :settings-keys project-model/irc-keys}]]
              (chatroom-item project-id settings owner chat-spec))]]])))))

(def status-styles
  {"badge" {:label "Badge" :string ".png?style=badge"}
   "shield" {:label "Shield" :string ".svg?style=shield"}
   "svg" {:label "Badge" :string ".svg?style=svg"}})

(def status-formats
  {"image" {:label "Image URL"
            :template :image}
   "markdown" {:label "Markdown"
               :template #(str "[![CircleCI](" (:image %) ")](" (:target %) ")")}
   "textile" {:label "Textile"
              :template #(str "!" (:image %) "!:" (:target %))}
   "rdoc" {:label "Rdoc"
           :template #(str "{<img src=\"" (:image %) "\" alt=\"CircleCI\" />}[" (:target %) "]")}
   "asciidoc" {:label "AsciiDoc"
               :template #(str "image:" (:image %) "[\"CircleCI\", link=\"" (:target %) "\"]")}
   "rst" {:label "reStructuredText"
          :template #(str ".. image:: " (:image %) "\n    :target: " (:target %))}
   "pod" {:label "pod"
          :template #(str "=for HTML <a href=\"" (:target %) "\"><img src=\"" (:image %) "\"></a>")}})

(defn status-badges [project-data owner]
  (let [project (:project project-data)
        oss (project-model/feature-enabled? project :oss)
        ;; Get branch selection or the empty string for the default branch.
        branches (branch-names project-data)
        branch (get-in project-data [:status-badges :branch])
        branch (or (some #{branch} branches) "")
        ;; Get token selection, or the empty string for no token. Tokens must have "status" scope.
        ;; OSS projects default to no token, private projects default to the first available.
        ;; If a token is required, but unavailable, no token is selected and the UI shows a warning.
        tokens (filter #(= (:scope %) "status") (:tokens project-data))
        token (get-in project-data [:status-badges :token])
        token (some #{token} (cons "" (map :token tokens)))
        token (str (if (or oss (some? token)) token (-> tokens first :token)))
        ;; Generate the status badge with current settings.
        project-name (vcs-url/project-name (:vcs_url project))
        gh-path (if (seq branch) (str project-name "/tree/" (gstring/urlEncode branch)) project-name)
        target (str (.. js/window -location -origin) "/gh/" gh-path)
        style (get-in project-data [:status-badges :style] "svg")
        image (str target (get-in status-styles [style :string]))
        image (if (seq token) (str image "&circle-token=" token) image)
        format (get-in project-data [:status-badges :format] "markdown")
        code ((:template (status-formats format)) {:image image :target target})]
    (om/component
     (html
      [:section.status-page
       [:article
        [:h2 "Status badges for " project-name]
        [:div "Use this tool to easily create embeddable status badges. Perfect for your project's README or wiki!"]
        [:div.status-page-inner
         [:form
          [:div.branch
           [:h4 "Branch"]
           [:select.form-control {:value branch
                                  :on-change #(utils/edit-input owner (conj state/project-data-path :status-badges :branch) %)}
            [:option {:value ""} "Default"]
            [:option {:disabled "disabled"} "-----"]
            (for [branch branches]
              [:option {:value branch} branch])]]

          [:div.token
           [:h4 "API Token"]
           (when-not (or oss (seq token))
             [:p [:span.warning "Warning: "] "Private projects require an API token - " [:a {:href "#api"} "add one with status scope"] "."])
           [:select.form-control {:value token
                                  :on-change #(utils/edit-input owner (conj state/project-data-path :status-badges :token) %)}
            [:option {:value ""} "None"]
            [:option {:disabled "disabled"} "-----"]
            (for [{:keys [token label]} tokens]
              [:option {:value token} label])]]

          ;; Hide style selector until "badge" style is improved. See PR #3140 discussion.
          #_[:div.style
             [:h4 "Style"]
             [:fieldset
              (for [[id {:keys [label]}] status-styles]
                [:label.radio
                 [:input {:name "branch" :type "radio" :value id :checked (= style id)
                          :on-change #(utils/edit-input owner (conj state/project-data-path :status-badges :style) %)}]
                 label])]]

          [:div.preview
           [:h4 "Preview"]
           [:img {:src image}]]

          [:div.embed
           [:h4 "Embed Code"]
           [:select.form-control {:value format
                                  :on-change #(utils/edit-input owner (conj state/project-data-path :status-badges :format) %)}
            (for [[id {:keys [label]}] status-formats]
              [:option {:value id} label])]
           [:textarea.dumb.form-control {:readonly true
                                         :value code
                                         :on-click #(.select (.-target %))}]]]]]]))))

(defn ssh-keys [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            project-id (project-model/id project)
            {:keys [hostname private-key]
             :or {hostname "" private-key ""}} (:new-ssh-key project-data)]
        (html
         [:section.sshkeys-page
          [:article
           [:h2 "SSH keys for " (vcs-url/project-name (:vcs_url project))]
           [:div.sshkeys-inner
            [:p "Add keys to the build VMs that you need to deploy to your machines. If the hostname field is blank, the key will be used for all hosts."]
            [:form
             [:input#hostname {:required true, :type "text" :value (str hostname)
                               :on-change #(utils/edit-input owner (conj state/project-data-path :new-ssh-key :hostname) %)}]
             [:label {:placeholder "Hostname"}]
             [:textarea#privateKey {:required true :value (str private-key)
                                    :on-change #(utils/edit-input owner (conj state/project-data-path :new-ssh-key :private-key) %)}]
             [:label {:placeholder "Private Key"}]
             (forms/managed-button
              [:input#submit.btn
               {:data-failed-text "Failed",
                :data-success-text "Saved",
                :data-loading-text "Saving..",
                :value "Submit",
                :type "submit"
                :on-click #(raise! owner [:saved-ssh-key {:project-id project-id
                                                          :ssh-key {:hostname hostname
                                                                    :private_key private-key}}])}])]
            (when-let [ssh-keys (seq (:ssh_keys project))]
              (om/build table/table
                        {:rows ssh-keys
                         :columns [{:header "Hostname"
                                    :cell-fn :hostname}
                                   {:header "Fingerprint"
                                    :cell-fn :fingerprint}
                                   {:header "Remove"
                                    :type #{:shrink :right}
                                    :cell-fn
                                    (fn [key]
                                      (table/action-button
                                       #(raise! owner [:deleted-ssh-key (-> key
                                                                            (select-keys [:hostname :fingerprint])
                                                                            (assoc :project-id project-id))])
                                       (icon/delete)))}]}))]]])))))

(defn checkout-key-link [key project user]
  (cond (= "deploy-key" (:type key))
        (case (:vcs-type project)
          "bitbucket" (str (bb-utils/http-endpoint) "/" (vcs-url/project-name (:vcs_url project)) "/admin/deploy-keys/")
          "github" (str (gh-utils/http-endpoint) "/" (vcs-url/project-name (:vcs_url project)) "/settings/keys"))
        (and (= "github-user-key" (:type key)) (= (:login key) (:login user)))
        (str (gh-utils/http-endpoint) "/settings/ssh")
        (and (= "bitbucket-user-key" (:type key))
             (= (:login key) (-> user :bitbucket :login)))
        (str (bb-utils/http-endpoint) "/account/user/" (:login key)
             "/ssh-keys/")
        :else nil))

(defn checkout-key-description [key project]
  (case (:type key)
    "deploy-key" (str (vcs-url/project-name (:vcs_url project)) " deploy key")
    "github-user-key" (str (:login key) " user key")
    "bitbucket-user-key" (str (:login key) " user key")
    nil))

(defmulti add-user-key-section (fn [data owner] (-> data :project-data :project :vcs-type keyword)))

(defmethod add-user-key-section :github [data owner]
  (reify
    om/IRender
    (render [_]
      (let [user (:user data)
            project-data (:project-data data)
            project (:project project-data)
            checkout-keys (:checkout-keys project-data)
            project-id (project-model/id project)
            project-name (vcs-url/project-name (:vcs_url project))]
        (html
         [:div.add-key
          [:h4 "Add user key"]
          [:p
           "If a deploy key can't access all of your project's private dependencies, we can configure it to use an SSH key with the same level of access to GitHub repositories that you have."]
          [:div.authorization
           (if-not (user-model/public-key-scope? user)
             (list
              [:p "In order to do so, you'll need to grant authorization from GitHub to the \"admin:public_key\" scope. This will allow us to add a new authorized public key to your GitHub account."]
              [:a.btn.ghu-authorize
               {:href (gh-utils/auth-url :scope ["admin:public_key" "user:email" "repo"])
                :title "Grant admin:public_key authorization so that we can add a new SSH key to your GitHub account"}
               "Authorize w/ GitHub " [:i.fa.fa-github]])

             [:div.request-user
              (forms/managed-button
               [:input.btn
                {:tooltip "{ title: 'Create a new user key for this project, with access to all of the projects of your GitHub account.', animation: false }"
                 :type "submit"
                 :on-click #(raise! owner [:new-checkout-key-clicked {:project-id project-id
                                                                      :project-name project-name
                                                                      :key-type "github-user-key"}])
                 :value (str "Create and add " (:login user) " user key" )
                 :data-loading-text "Saving..."
                 :data-success-text "Saved"}])])]])))))

(defmethod add-user-key-section :bitbucket [data owner]
  (reify
    om/IRender
    (render [_]
      (let [user (:user data)
            project-data (:project-data data)
            project (:project project-data)
            checkout-keys (:checkout-keys project-data)
            project-id (project-model/id project)
            project-name (vcs-url/project-name (:vcs_url project))]
        (html
         [:div.add-key
          [:h4 "Create user key"]
          [:p
           "If a deploy key can't access all of your project's private dependencies, we can help you setup an SSH key with the same level of access to Bitbucket repositories that you have."]
          [:div.authorization
           [:div.request-user
            (forms/managed-button
             [:input.btn
              {:tooltip "{ title: 'Create a new user key for this project, with access to all of the projects of your Bitbucket account.', animation: false }"
               :type "submit"
               :on-click #(raise! owner [:new-checkout-key-clicked {:project-id project-id
                                                                    :project-name project-name
                                                                    :key-type "bitbucket-user-key"}])
               :value (str "Create " (:login user) " user key" )
               :data-loading-text "Creating..."
               :data-success-text "Created"}])]]])))))

(defn bitbucket-add-user-key-instructions [data owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [user key]} data]
        (html
         [:div.add-key
          [:h4 "Add user key to Bitbucket"]
          [:p "You now need to add this key to your "
           [:a {:href
                (str "https://bitbucket.org/account/user/"
                     (-> user :bitbucket :login)
                     "/ssh-keys/")}
            "Bitbucket account SSH keys"]]
          [:p
           [:ol
            [:li "Navigate to your " [:a {:href (checkout-key-link key nil user)}
                                      "Bitbucket account SSH keys"]]
            [:li "Click \"Add key\""]
            [:li
             "Copy this public key and paste it into the new key on Bitbucket"
             [:pre.wrap-words
              (:public_key key)]]
            [:li "Save the new key"]]]])))))

(defn checkout-ssh-keys [data owner]
  (reify
    om/IRender
    (render [_]
      (let [project-data (:project-data data)
            user (:user data)
            project (:project project-data)
            project-id (project-model/id project)
            project-name (vcs-url/project-name (:vcs_url project))
            vcs-name (-> project :vcs-type utils/prettify-vcs_type)
            checkout-keys (:checkout-keys project-data)]
        (html
         [:section.checkout-page
          [:article
           [:h2 "Checkout keys for " project-name]
           [:div.checkout-page-inner
            (if (nil? checkout-keys)
              [:div.loading-spinner common/spinner]

              [:div
               (if-not (seq checkout-keys)
                 [:p "No checkout key is currently configured! We won't be able to check out your project for testing :("]
                 [:div
                  [:p
                   "Here are the keys we can currently use to check out your project, submodules, "
                   "and private " vcs-name " dependencies. The currently preferred key is marked, but "
                   "we will automatically fall back to the other keys if the preferred key is revoked."]
                  (om/build table/table
                            {:rows checkout-keys
                             :columns [{:header "Description"
                                        :cell-fn #(if-let [vcs-link (checkout-key-link % project user)]
                                                    [:a {:href vcs-link :target "_blank"}
                                                     (checkout-key-description % project) " "
                                                     (case (vcs-url/vcs-type project-id)
                                                       "bitbucket" [:i.fa.fa-bitbucket]
                                                       "github" [:i.fa.fa-github])]
                                                    (checkout-key-description % project))}
                                       {:header "Fingerprint"
                                        :cell-fn :fingerprint}
                                       {:header "Preferred"
                                        :type #{:right :shrink}
                                        :cell-fn #(when (:preferred %)
                                                    (html [:i.material-icons "done"]))}
                                       {:header "Remove"
                                        :type #{:shrink :right}
                                        :cell-fn
                                        (fn [key]
                                          (table/action-button
                                           #(raise! owner [:delete-checkout-key-clicked {:project-id project-id
                                                                                         :project-name project-name
                                                                                         :fingerprint (:fingerprint key)}])
                                           (icon/delete)))}]})])
               (when-not (seq (filter #(= "deploy-key" (:type %)) checkout-keys))
                 [:div.add-key
                  [:h4 "Add deploy key"]
                  [:p
                   "Deploy keys are the best option for most projects: they only have access to a single " vcs-name " repository."]
                  [:div.request-user
                   (forms/managed-button
                    [:input.btn
                     {:type "submit"
                      :on-click #(raise! owner
                                         [:new-checkout-key-clicked {:project-id project-id
                                                                     :project-name project-name
                                                                     :key-type "deploy-key"}])
                      :title "Create a new deploy key, with access only to this project."
                      :value (str "Create and add " project-name " deploy key")
                      :data-loading-text "Saving..."
                      :data-success-text "Saved"}])]])
               (when-not (some #{"github-user-key" "bitbucket-user-key"} (map :type checkout-keys))
                 (om/build add-user-key-section data))
               (when-let [bb-user-key (first (filter (fn [key] (and
                                                                (= (:type key)
                                                                   "bitbucket-user-key")
                                                                (= (:login key)
                                                                   (-> user :bitbucket :login))))
                                                     checkout-keys))]
                 (om/build bitbucket-add-user-key-instructions {:user user :key bb-user-key}))


               [:div.help-block
                [:h2 "About checkout keys"]
                [:h4 "What is a deploy key?"]
                [:p "A deploy key is a repo-specific SSH key. " vcs-name " has the public key, and we store the private key. The deployment key gives CircleCI access to a single repository."]
                [:p "If you want to push to your repository from builds, please add a user key as described below or manually add " [:a (open-ext {:href "https://circleci.com/docs/adding-read-write-deployment-key/"}) "read-write deployment key"]"."]
                [:h4 "What is a user key?"]
                [:p "A user key is a user-specific SSH key. " vcs-name " has the public key, and we store the private key. Possession of the private key gives the ability to act as that user, for purposes of 'git' access to repositories."]
                [:h4 "How are these keys used?"]
                [:p "When we build your project, we install the private key into the .ssh directory, and configure ssh to use it when communicating with your version control provider. Therefore, it gets used for:"]
                [:ul
                 [:li "checking out the main project"]
                 [:li "checking out any " vcs-name "-hosted submodules"]
                 [:li "checking out any " vcs-name "-hosted private dependencies"]
                 [:li "automatic git merging/tagging/etc."]]
                [:p]
                [:p "That's why a deploy key isn't sufficiently powerful for projects with additional private dependencies!"]
                [:h4 "What about security?"]
                [:p "The private keys of the checkout keypairs we generate never leave our systems (only the public key is transmitted to" vcs-name "), and are safely encrypted in storage. However, since they are installed into your build containers, any code that you run in CircleCI can read them. You shouldn't push untrusted code to CircleCI!"]
                [:h4 "Isn't there a middle ground between deploy keys and user keys?"]
                [:p "Not really :("]
                [:p "Deploy keys and user keys are the only key types that " vcs-name " supports. Deploy keys are globally unique (i.e. there's no way to make a deploy key with access to multiple repositories) and user keys have no notion of \\scope\\ separate from the user they're associated with."]
                [:p "Your best bet, for fine-grained access to more than one repo, is to create what GitHub calls a "
                 [:a {:href "https://help.github.com/articles/managing-deploy-keys#machine-users"} "machine user"]
                 ". Give this user exactly the permissions your build requires, and then associate its user key with your project on CircleCI."
                 (when (= "bitbucket" (:vcs-type project))
                   "The same technique can be applied for Bitbucket projects.")]]])]]])))))

(defn scope-popover-html []
  ;; nb that this is a bad idea in general, but should be ok for rarely used popovers
  (hiccup->html-str
   [:div
    [:p "A token's scope limits what can be done with it."]

    [:h5 "Status"]
    [:p
     "Allows read-only access to the build status (passing, failing, etc) of any branch of the project. Its intended use is "
     [:a (open-ext {:target "_blank" :href "https://circleci.com/docs/status-badges/"}) "sharing status badges"]
     " and "
     [:a (open-ext {:target "_blank", :href "https://circleci.com/docs/polling-project-status/"}) "status polling tools"]
     " for private projects."]

    [:h5 "Build Artifacts"]
    [:p "Allows read-only access to build artifacts of any branch of the project. Its intended use is for serving files to deployment systems."]

    [:h5 "All"]
    [:p "Allows full read-write access to this project in CircleCI. It is intended for full-fledged API clients which only need to access a single project."]]))

(defn api-tokens [project-data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/popover "#scope-popover-hack" {:html true
                                            :delay 0
                                            :animation false
                                            :placement "left"
                                            :title "Scope"
                                            :content (scope-popover-html)}))
    om/IRender
    (render [_]
      (let [project (:project project-data)
            project-id (project-model/id project)
            {:keys [scope label]
             :or {scope "status" label ""}} (:new-api-token project-data)]
        (html
         [:section.circle-api-page
          [:article
           [:h2 "API tokens for " (vcs-url/project-name (:vcs_url project))]
           [:div.circle-api-page-inner
            [:p "Create and revoke project-specific API tokens to access this project's details using our API. First choose a scope "
             [:i.fa.fa-question-circle#scope-popover-hack {:title "Scope"}]
             " and then create a label."]
            [:form
             [:select.form-control {:name "scope" :value scope
                                    :on-change #(utils/edit-input owner (conj state/project-data-path :new-api-token :scope) %)}
              [:option {:value "status"} "Status"]
              [:option {:value "view-builds"} "Build Artifacts"]
              [:option {:value "all"} "All"]]
             [:input
              {:required true, :type "text" :value (str label)
               :on-change #(utils/edit-input owner (conj state/project-data-path :new-api-token :label) %)}]
             [:label {:placeholder "Token label"}]
             (forms/managed-button
              [:input
               {:data-failed-text "Failed",
                :data-success-text "Created",
                :data-loading-text "Creating...",
                :on-click #(raise! owner [:saved-project-api-token {:project-id project-id
                                                                    :api-token {:scope scope
                                                                                :label label}}])
                :value "Create token",
                :type "submit"}])]
            (when-let [tokens (seq (:tokens project-data))]
              (om/build table/table
                        {:rows tokens
                         :columns [{:header "Scope"
                                    :cell-fn :scope}
                                   {:header "Label"
                                    :cell-fn :label}
                                   {:header "Token"
                                    :cell-fn :token}
                                   {:header "Created"
                                    :cell-fn :time}
                                   {:header "Remove"
                                    :type #{:shrink :right}
                                    :cell-fn
                                    (fn [token]
                                      (table/action-button
                                       #(raise! owner [:deleted-project-api-token {:project-id project-id
                                                                                   :token (:token token)}])
                                       (icon/delete)))}]}))]]])))))

(defn heroku [data owner]
  (reify
    om/IRender
    (render [_]
      (let [project-data (:project-data data)
            user (:user data)
            project (:project project-data)
            project-id (project-model/id project)
            login (:login user)]
        (html
         [:section.heroku-api
          [:article
           [:h2 "Set personal Heroku API key for " (vcs-url/project-name (:vcs_url project))]
           [:div.heroku-step
            [:h4 "Step 1: Heroku API key"]
            [:div (when (:heroku_api_key user)
                    [:p "Your Heroku key is entered. Great!"])
             [:p (:heroku_api_key user)]
             [:div (when-not (:heroku_api_key user)
                     (om/build account/heroku-key {:current-user user} {:opts {:project-page? true}}))]
             [:div (when (:heroku_api_key user)
                     [:p
                      "You can edit your Heroku key from your "
                      [:a {:href "/account/heroku"} "account page"] "."])]]]
           [:div.heroku-step
            [:h4 "Step 2: Associate a Heroku SSH key with your account"]
            [:span "Current deploy user: "
             [:strong (or (:heroku_deploy_user project) "none") " "]
             [:i.fa.fa-question-circle
              {:data-bind "tooltip: {}",
               :title "This will affect all deploys on this project. Skipping this step will result in permission denied errors when deploying."}]]
            [:form.api
             (if (= (:heroku_deploy_user project) (:login user))
               (forms/managed-button
                [:input.btn.btn-danger.remove-user
                 {:data-success-text "Saved",
                  :data-loading-text "Saving...",
                  :on-click #(raise! owner [:removed-heroku-deploy-user {:project-id project-id}])
                  :value "Remove Heroku Deploy User",
                  :type "submit"}])

               (forms/managed-button
                [:input.btn.btn-primary.set-user
                 {:data-success-text "Saved",
                  :data-loading-text "Saving...",
                  :on-click #(raise! owner [:set-heroku-deploy-user {:project-id project-id
                                                                     :login login}])
                  :value (str "Set user to " (:login user)),
                  :type "submit"}]))]]
           [:div.heroku-step
            [:h4
             "Step 3: Add deployment settings to your "
             [:a (open-ext {:href "https://circleci.com/docs/configuration/#deployment"}) "circle.yml file"] " (example below)."]
            [:pre
             [:code
              "deployment:\n"
              "  staging:\n"
              "    branch: master\n"
              "    heroku:\n"
              "      appname: foo-bar-123"]]]]])))))

(defn other-deployment [project-data owner]
  (om/component
   (html
    [:section
     [:article
      [:h2
       "Other deployments for " (vcs-url/project-name (get-in project-data [:project :vcs_url]))]
      [:div.doc
       [:p "CircleCI supports deploying to any server, using custom commands. See "
        [:a (open-ext {:target "_blank",
                       :href "https://circleci.com/docs/configuration#deployment"})
         "our deployment documentation"]
        " to set it up."]]]])))

(defn aws-keys-form [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            inputs (inputs/get-inputs-from-app-state owner)

            settings (utils/deep-merge (get-in project [:aws :keypair])
                                       (get-in inputs [:aws :keypair]))
            {:keys [access_key_id secret_access_key]} settings

            project-id (project-model/id project)
            input-path (fn [& ks] (apply conj state/inputs-path :aws :keypair ks))]
        (html
         [:div.aws-page-inner
          [:p "Set the AWS keypair to be used for authenticating against AWS services during your builds. "
           "Credentials are installed on your containers into the " [:code "~/.aws/config"] " and "
           [:code "~/.aws/credentials"] " properties files. These are read by common AWS libraries such as "
           [:a {:href "http://aws.amazon.com/documentation/sdk-for-java/"} "the Java SDK"] ", "
           [:a {:href "https://boto.readthedocs.org/en/latest/"} "Python's boto"] ", and "
           [:a {:href "http://rubygems.org/gems/aws-sdk"} "the Ruby SDK"] "."]
          [:p "We recommend that you create a unique "
           [:a (open-ext {:href "http://docs.aws.amazon.com/general/latest/gr/root-vs-iam.html"}) "IAM user"]
           " for use by CircleCI."]
          [:form
           [:input#access-key-id
            {:required true, :type "text", :value (or access_key_id "")
             :on-change #(utils/edit-input owner (input-path :access_key_id) %)}]
           [:label {:placeholder "Access Key ID"}]

           [:input#secret-access-key
            {:required true,
             :type "text",
             :value (or secret_access_key "")
             :auto-complete "off"
             :on-change #(utils/edit-input owner (input-path :secret_access_key) %)}]
           [:label {:placeholder "Secret Access Key"}]

           [:div.buttons
            (forms/managed-button
             [(if (and access_key_id secret_access_key) :input.save :input)
              {:data-failed-text "Failed"
               :data-success-text "Saved"
               :data-loading-text "Saving..."
               :value "Save AWS keys"
               :type "submit"
               :on-click #(raise! owner [:saved-project-settings {:project-id project-id :merge-paths [[:aws :keypair]]}])}])
            (when (and access_key_id secret_access_key)
              (forms/managed-button
               [:input.remove {:data-failed-text "Failed"
                               :data-success-text "Cleared"
                               :data-loading-text "Clearing..."
                               :value "Clear AWS keys"
                               :type "submit"
                               :on-click #(do
                                            (raise! owner [:edited-input {:path (input-path) :value nil}])
                                            (raise! owner [:saved-project-settings {:project-id project-id}]))}]))]]])))))

(defn aws [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)]
        (html
         [:section.aws-page
          [:article
           [:h2 "AWS keys for " (vcs-url/project-name (:vcs_url project))]
           (om/build aws-keys-form project-data)]])))))


(defn aws-codedeploy-app-name [project-data owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div
        [:form
         [:input#application-name
          {:required true, :type "text"
           :on-change #(utils/edit-input owner (conj state/inputs-path :project-settings-codedeploy-app-name) %)}]
         [:label {:placeholder "Application Name"}]
         [:input {:value "Add app settings",
                  :type "submit"
                  :on-click #(raise! owner [:new-codedeploy-app-name-entered])}]]]))))

(defn aws-codedeploy-app-details [project-data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [project (:project project-data)
            applications (get-in project [:aws :services :codedeploy])
            [app-name _] (first applications)]
        (utils/popover "#app-root-popover-hack"
                       {:html true :delay 0 :animation false
                        :placement "right" :title "Application Root"
                        :content (hiccup->html-str [:p "The directory in your repo to package up into an application revision. "
                                                    "This is relative to your repo's root, " [:code "/"] " means the repo's root "
                                                    "directory, " [:code "/app"] " means the app directory in your repo's root "
                                                    "directory."])})
        (utils/popover "#bucket-popover-hack"
                       {:html true :delay 0 :animation false
                        :placement "right" :title "Revision Location: Bucket Name"
                        :content (hiccup->html-str [:p "The name of the S3 bucket CircleCI should store application revisions for \"" (name app-name) "\" in."])})
        (utils/popover "#key-pattern-popover-hack"
                       {:html true :delay 0 :animation false
                        :placement "right" :title "Revision Location: Key Pattern"
                        :content (hiccup->html-str [:p "A template used to construct S3 keys for storing application revisions."
                                                    "You can use " [:a (open-ext {:href "https://circleci.com/docs/continuous-deployment-with-aws-codedeploy/#key-patterns"}) "substitution variables"]
                                                    " in the Key Pattern to generate a unique key for each build."])})))
    om/IRender
    (render [_]
      (let [project (:project project-data)
            inputs (inputs/get-inputs-from-app-state owner)
            applications (utils/deep-merge (get-in project [:aws :services :codedeploy])
                                           (get-in inputs [:aws :services :codedeploy]))

            [app-name settings] (first applications)
            {:keys [bucket key_pattern]} (-> settings :revision_location :s3_location)
            application-root (:application_root settings)
            aws-region (:region settings)

            project-id (project-model/id project)
            input-path (fn [& ks] (apply conj state/inputs-path :aws :services :codedeploy ks))]
        (html
         [:form
          [:legend (name app-name)]

          [:fieldset
           [:select.form-control {:class (when (not aws-region) "placeholder")
                                  :value (or aws-region "")
                                  ;; Updates the project cursor in order to trigger a re-render
                                  :on-change #(utils/edit-input owner (conj state/project-path :aws :services :codedeploy app-name :region) %)}
            [:option {:value ""} "Choose AWS Region..."]
            [:option {:disabled "disabled"} "-----"]
            [:option {:value "us-east-1"} "us-east-1"]
            [:option {:value "us-west-2"} "us-west-2"]]

           [:div.input-with-help
            [:input#application-root
             {:required true, :type "text", :value (or application-root "")
              :on-change #(utils/edit-input owner (input-path app-name :application_root) %)}]
            [:label {:placeholder "Application Root"}]
            [:i.fa.fa-question-circle#app-root-popover-hack {:title "Application Root"}]]]

          [:fieldset
           [:h5 "Revision Location"]
           [:div.input-with-help
            [:input#s3-bucket
             {:required true, :type "text", :value (or bucket "")
              :on-change #(utils/edit-input owner (input-path app-name :revision_location :s3_location :bucket) %)}]
            [:label {:placeholder "Bucket Name"}]
            [:i.fa.fa-question-circle#bucket-popover-hack {:title "S3 Bucket Name"}]]

           [:div.input-with-help
            [:input#s3-key-prefix
             {:required true, :type "text", :value (or key_pattern "")
              :on-change #(utils/edit-input owner (input-path app-name :revision_location :s3_location :key_pattern) %)}]
            [:label {:placeholder "Key Pattern"}]
            [:i.fa.fa-question-circle#key-pattern-popover-hack {:title "S3 Key Pattern"}]]]

          [:div.buttons
           (forms/managed-button
            [:input.save {:data-failed-text "Failed",
                          :data-success-text "Saved",
                          :data-loading-text "Saving...",
                          :value "Save app",
                          :type "submit"
                          :on-click #(do
                                       (raise! owner [:edited-input {:path (input-path app-name :revision_location :revision_type) :value "S3"}])
                                       (raise! owner [:saved-project-settings {:project-id project-id
                                                                               :merge-paths [[:aws :services :codedeploy]]}]))}])
           (forms/managed-button
            [:input.remove {:data-failed-text "Failed",
                            :data-success-text "Removed",
                            :data-loading-text "Removing...",
                            :value "Remove app",
                            :type "submit"
                            :on-click #(do
                                         (raise! owner [:edited-input {:path (input-path) :value nil}])
                                         (raise! owner [:saved-project-settings {:project-id project-id}]))}])]])))))

(defn aws-codedeploy [project-data owner]
  (reify
    om/IRender
    (render [_]
      (let [project (:project project-data)
            applications (get-in project [:aws :services :codedeploy])
            app-name (some-> applications first key)]
        (html
         [:section.aws-codedeploy
          [:article
           [:h2 "CodeDeploy application settings for " (vcs-url/project-name (:vcs_url project))]
           [:p "CodeDeploy is an AWS service for deploying to your EC2 instances. "
            "Check out our " [:a (open-ext {:href "https://circleci.com/docs/continuous-deployment-with-aws-codedeploy/"}) "getting started with CodeDeploy"]
            " guide for detailed information on getting set up."]
           [:div.aws-page-inner
            [:div.aws-codedeploy-step
             [:h4 "Step 1"]
             (om/build aws-keys-form project-data)]

            [:div.aws-codedeploy-step
             [:h4 "Step 2"]
             [:p "[Optional] Configure application-wide settings."]
             [:p "This is useful if you deploy the same app to multiple deployment groups "
              "(e.g. staging, production) depending on which branch was built. "
              "With application settings configured in the UI you only need to set the "
              "deployment group and, optionally, deployment configuration, in each deployment "
              "block in your " [:a (open-ext {:href "https://circleci.com/docs/configuration/#deployment"}) "circle.yml file"] ". "
              "If you skip this step you will need to add all deployment settings into your circle.yml file."]
             (if (not (seq applications))
               ;; No settings set, need to get the application name first
               (om/build aws-codedeploy-app-name project-data)
               ;; Once we have an application name we can accept the rest of the settings
               (om/build aws-codedeploy-app-details project-data))]
            [:div.aws-codedeploy-step
             [:h4 "Step 3"]
             [:p "Add deployment settings to your "
              [:a (open-ext {:href "https://circleci.com/docs/configuration/#deployment"}) "circle.yml file"]
              " (example below)."]
             [:pre
              [:code
               "deployment:\n"
               "  staging:\n"
               "    branch: master\n"
               "    codedeploy:\n"
               (if app-name
                 (str "      " (name app-name) ":\n"
                      "        deployment_group: my-deployment-group\n")
                 (str "      appname-1234:\n"
                      "        application_root: /\n"
                      "        region: us-east-1\n"
                      "        revision_location:\n"
                      "          revision_type: S3\n"
                      "          s3_location:\n"
                      "            bucket: my-bucket\n"
                      "            key_pattern: appname-1234-{BRANCH}-{SHORT_COMMIT}\n"
                      "        deployment_group: my-deployment-group\n"))]]]]]])))))

(defn modal [{:keys [id title body-component body-params error-message]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.modal.fade {:id id :data-component `modal}
         [:div.modal-dialog
          [:div.modal-content
           [:div.modal-header
            [:div.modal-title title]
            [:i.material-icons.modal-close {:data-dismiss "modal"} "clear"]]
           [:div.modal-body.upload
            (om/build common/flashes error-message)
            (om/build body-component body-params)]]]]))))

(defn p12-upload-form [{:keys [project-name vcs-type]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:description nil
       :password nil
       :file-name nil
       :file-content nil
       :dragged-over? false})

    om/IRenderState
    (render-state [_ {:keys [description password file-name file-content dragged-over?]}]
      (let [close-modal-fn #(.modal ((aget js/window "$") "#p12-upload-modal") "hide")
            clear-form-fn #(om/set-state! owner {:description nil
                                                 :password nil
                                                 :file-name nil
                                                 :file-content nil})
            file-selected-fn (fn [file]
                               (om/set-state! owner :file-name (aget file "name"))
                               (doto (js/FileReader.)
                                 (aset "onload" #(om/set-state! owner :file-content (aget % "target" "result")))
                                 (.readAsBinaryString file)))]
        (html
          [:div {:data-component `p12-upload-form}
           [:div
            [:label.label "Description"]
            [:input.dumb.text-input
             {:type "text" :value description
              :on-change #(om/set-state! owner :description (aget % "target" "value"))}]]

           [:div
            [:label.label "Password (Optional)"]
            [:input.dumb.text-input
             {:type "password" :value password
              :on-change #(om/set-state! owner :password (aget % "target" "value"))}]]

           [:div
            [:label.label "File"]
            [:div.drag-and-drop-area {:class (when dragged-over? "dragged-over")
                                      :on-drag-over #(do (.stopPropagation %)
                                                         (.preventDefault %)
                                                         (om/set-state! owner :dragged-over? true))
                                      :on-drag-leave #(om/set-state! owner :dragged-over? false)
                                      :on-drop #(do (.stopPropagation %)
                                                    (.preventDefault %)
                                                    (om/set-state! owner :dragged-over? false)
                                                    (file-selected-fn (aget % "dataTransfer" "files" 0)))}
             (if file-name
               [:div file-name]
               [:div "Drop your files here or click " [:b "Choose file"] " below to select them manually!"])
             [:label.p12-file-input
              [:input.hidden-p12-file-input {:type "file"
                                             :on-change #(file-selected-fn (aget % "target" "files" 0))}]
              [:i.material-icons "file_upload"]
                "Choose file"]]]

           [:hr]
           [:div.buttons
            (forms/managed-button
              [:input.upload {:data-failed-text "Failed",
                            :data-success-text "Uploaded",
                            :data-loading-text "Uploading...",
                            :value "Upload",
                            :type "submit"
                            :disabled (not (and file-content description))
                            :on-click #(raise! owner [:upload-p12 {:project-name project-name
                                                                  :vcs-type vcs-type
                                                                  :description description
                                                                  :password (or password "")
                                                                  :file-content (base64/encodeString file-content)
                                                                  :file-name file-name
                                                                  :on-success (comp clear-form-fn close-modal-fn)}])}])]])))))

(defn p12-key-table [{:keys [rows]} owner]
  (reify
    om/IRender
    (render [_]
      (om/build table/table
                {:rows rows
                 :columns [{:header "Description"
                            :cell-fn :description}
                           {:header "Filename"
                            :type :shrink
                            :cell-fn :filename}
                           {:header "ID"
                            :type :shrink
                            :cell-fn :id}
                           {:header "Uploaded"
                            :type :shrink
                            :cell-fn (comp datetime/as-time-since :uploaded_at)}
                           {:header "Remove"
                            :type #{:shrink :right}
                            :cell-fn
                            (fn [row]
                              (table/action-button
                               #(raise! owner [:delete-p12 (select-keys row [:project-name :vcs-type :id])])
                               (icon/delete)))}]}))))

(defn no-keys-empty-state [{:keys [project-name]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div {:data-component `no-keys-empty-state}
         [:i.octicon.octicon-key]
         [:div.info
          [:span.highlight project-name]
          [:span " has no "]
          [:span.highlight "Apple Code Signing Identities"]
          [:span "  yet"]]
         [:a.btn.upload-key-button {:data-target "#p12-upload-modal"
                                    :data-toggle "modal"}
          "Upload Key"]
         [:div.sub-info "Apple Code Signing requires a valid Code Signing Identity (p12) file"]]))))

(defn code-signing [{:keys [project-data error-message]} owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [project osx-keys]} project-data
            project-name (vcs-url/project-name (:vcs_url project))
            vcs-type (project-model/vcs-type project)]
        (html
          [:section.code-signing-page {:data-component `code-signing}
           [:article
            [:div.header
             [:div.title "Apple Code Signing Keys"]
             [:a.btn.upload-key-button {:data-target "#p12-upload-modal"
                                        :data-toggle "modal"}
              "Upload Key"]]
            [:hr.divider]
            [:div.info "The following code-signing identities will be added to the system keychain when your build
                        begins, and will be available to sign iOS and OSX apps. For more information about code-signing
                        on CircleCI see our "
             [:a
              {:href "https://discuss.circleci.com/t/ios-code-signing/1231"}
              "code-signing documentation."]]
            (if-not (empty? osx-keys)
              (om/build p12-key-table {:rows (->> osx-keys
                                                  (map (partial merge {:project-name project-name
                                                                       :vcs-type vcs-type})))})
              (om/build no-keys-empty-state {:project-name project-name}))
            (om/build modal {:id "p12-upload-modal"
                             :title "Upload a New Apple Code Signing Key"
                             :body-component p12-upload-form
                             :body-params {:project-name project-name
                                           :vcs-type vcs-type}
                             :error-message error-message})]])))))

(defn project-settings [data owner]
  (reify
    om/IRender
    (render [_]
      (let [project-data (get-in data state/project-data-path)
            user (:current-user data)
            subpage (:project-settings-subpage data)
            error-message (get-in data state/error-message-path)]
        (html
         (if-not (get-in project-data [:project :vcs_url]) ; wait for project-settings to load
           [:div.loading-spinner-big common/spinner]
           [:div#project-settings
            ; Temporarly disable top level error messsage for the set of subpages while we
            ; transition them. Each subpage will eventually handle their own error messages.
            (when-not (contains? #{:code-signing} subpage)
              (om/build common/flashes error-message))
            [:div#subpage
             (condp = subpage
               :build-environment (om/build build-environment project-data)
               :parallel-builds (om/build parallel-builds data)
               :env-vars (om/build env-vars project-data)
               :advanced-settings (om/build advance project-data)
               :clear-caches (if (or (feature/enabled? :project-cache-clear-buttons)
                                     (config/enterprise?))
                               (om/build clear-caches project-data)
                               (om/build overview project-data))
               :setup (om/build dependencies project-data)
               :tests (om/build tests project-data)
               :hooks (om/build notifications project-data)
               :webhooks (om/build webhooks project-data)
               :badges (om/build status-badges project-data)
               :ssh (om/build ssh-keys project-data)
               :checkout (om/build checkout-ssh-keys {:project-data project-data :user user})
               :api (om/build api-tokens project-data)
               :heroku (om/build heroku {:project-data project-data :user user})
               :deployment (om/build other-deployment project-data)
               :aws (om/build aws project-data)
               :aws-codedeploy (om/build aws-codedeploy project-data)
               :code-signing (om/build code-signing {:project-data project-data :error-message error-message})
               (om/build overview project-data))]]))))))
