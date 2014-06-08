(ns frontend.components.project-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.models.build :as build-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.components.common :as common]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(def sidebar
  [:ul.side-list
   [:li.side-title "Project Settings"]
   [:li [:a {:href "edit"} "Overview"]]
   [:li.side-title "Tweaks"]
   [:li [:a {:href "#parallel-builds"} "Parallelism"]]
   [:li [:a {:href "#env-vars"} "Environment variables"]]
   [:li.side-title "Test Commands"]
   [:li [:a {:href "#setup"} "Dependencies"]]
   [:li [:a {:href "#tests"} "Tests"]]
   [:li.side-title "Notifications"]
   [:li [:a {:href "#hooks"} "Chatrooms"]]
   [:li [:a {:href "#webhooks"} "Webhooks"]]
   [:li.side-title "Permissions"]
   [:li [:a {:href "#ssh"} "SSH keys"]]
   [:li [:a {:href "#github"} "GitHub user"]]
   [:li [:a {:href "#api"} "API tokens"]]
   [:li.side-title "Build Artifacts"]
   [:li [:a {:href "#artifacts"} "Artifacts"]]
   [:li.side-title "Continuous Deployment"]
   [:li [:a {:href "#heroku"} "Heroku"]]
   [:li [:a {:href "#deployment"} "Other Deployments"]]])

(defn branch-picker [project-data controls-ch & {:keys [button-text channel-message channel-args]
                                                 :or {button-text "Start a build"
                                                      channel-message :started-edit-settings-build}}]
  (let [project (:project project-data)
        project-id (project-model/id project)
        default-branch (:default_branch project)
        settings-branch (get project-data :settings-branch default-branch)]
    [:form {:on-submit #(do (put! controls-ch [channel-message (merge {:project-id project-id
                                                                       :branch settings-branch}
                                                                      channel-args)])
                            false)}
     [:input {:name "branch"
              :required true
              :type "text"
              :value settings-branch
              ;; XXX typeahead
              :on-change #(utils/edit-input controls-ch state/project-settings-branch-path %)}]
     [:label {:placeholder "Test settings on..."}]
     [:input
      {:value button-text
       ;; XXX handle data-loading-text
       :data-loading-text "Starting..."
       :type "submit"}]]))

(defn overview [project-data controls-ch]
  [:div.project-settings-block
   [:h2 "How to configure " [:span (vcs-url/project-name (get-in project-data [:project :vcs_url]))]]
   [:ul.overview-options
    [:li.overview-item
     [:h4 "Option 1"]
     [:p
      "Do nothing! Circle infers many settings automatically. Works great for Ruby, Python, NodeJS, Java and Clojure. However, if it needs tweaks or doesn't work, see below."]]
    [:li.overview-item
     [:h4 "Option 2"]
     [:p
      "Override inferred settings and add new test commands "
      [:a {:href "#setup"} "through the web UI"]
      ". This works great for prototyping changes."]]
    [:li.overview-item
     [:h4 "Option 3"]
     [:p
      "Override all settings via a "
      [:a {:href "/docs/configuration"} "circle.yml file"]
      " in your repo. Very powerful."]]]])

(defn mini-parallelism-faq [project-data controls-ch]
  [:div.mini-faq
   [:div.mini-faq-item
    [:h3 "What are containers?"]
    [:p
     "Containers are what we call the virtual machines that your tests run in. Your current plan has "
     (get-in project-data [:plan :containers])
     " containers and supports up to "
     (plan-model/max-parallelism (:plan project-data))
     "x paralellism."]

    [:p "With 16 containers you could run:"]
    [:ul
     [:li "16 simultaneous builds at 1x parallelism"]
     [:li "8 simultaneous builds at 2x parallelism"]
     [:li "4 simultaneous builds at 4x parallelism"]
     [:li "2 simultaneous builds at 8x parallelism"]
     [:li "1 build at 16x parallelism"]]]
   [:div.mini-faq-item
    [:h3 "What is parallelism?"]
    [:p
     "We split your tests into groups, and run each group on different machines in parallel. This allows them run in a fraction of the time, for example:"]
    [:p]
    [:ul
     [:li "a 45 minute build fell to 18 minutes with 3x build speed,"]
     [:li
      "a 20 minute build dropped to 11 minutes with 2x build speed."]]
    [:p
     "Each machine is completely separated (sandboxed and firewalled) from the others, so that your tests can't conflict with each other: separate databases, file systems, process space, and memory."]
    [:p
     "For RSpec, Cucumber and Test::Unit, we'll automatically run your tests, splitting them appropriately among different machines. If you have a different test suite, you can "
     [:a
      {:href "/docs/parallel-manual-setup"}
      "control the parallelism directly"]
     "."]]
   [:div.mini-faq-item
    [:h3 "What do others think?"]
    [:blockquote
     [:i
      "The thing that sold us on Circle was the speed. Their tests run really really fast. We've never seen that before. One of our developers just pushes to branches so that Circle will run his tests, instead of testing on his laptop. The parallelization just works - we didn't have to tweak anything. Amazing service."]]
    [:ul
     [:li [:a {:href "http://zencoder.com/company/"} "Brandon Arbini"]]
     [:li [:a {:href "http://zencoder.com/"} "Zencoder.com"]]]]])

(defn parallel-label-classes [project-data parallelism]
  (concat
   []
   (when (> parallelism (plan-model/max-selectable-parallelism (:plan project-data))) ["disabled"])
   (when (= parallelism (get-in project-data [:project :parallel])) ["selected"])
   (when (not= 0 (mod (plan-model/usable-containers (:plan project-data)) parallelism)) ["bad_choice"])))

(defn parallelism-tile
  "Determines what we show when they hover over the parallelism option"
  [project-data parallelism]
  (let [plan (:plan project-data)
        project (:project project-data)
        project-id (project-model/id project)]
    (list
     [:div.parallelism-upgrades
      (if-not (= "trial" (:type plan))
        (cond (> parallelism (plan-model/max-parallelism plan))
              [:div.insufficient-plan
               "Your plan only allows up to "
               (plan-model/max-parallelism plan) "x parallelism."
               [:a {:href (routes/v1-org-settings-subpage {:org-id (:org_name plan)
                                                           :subpage "plan"})}
                "Upgrade"]]

              (> parallelism (plan-model/max-selectable-parallelism plan))
              [:div.insufficient-containers
               "Not enough containers available."
               [:a {:href (routes/v1-org-settings-subpage {:org-id (:org_name plan)
                                                           :subpage "containers"})}
                "Add More"]])

        (when (> parallelism (plan-model/max-selectable-parallelism plan))
          [:div.insufficient-trial
           "Trials only come with " (plan-model/usable-containers plan) " available containers."
               [:a {:href (routes/v1-org-settings-subpage {:org-id (:org_name plan)
                                                           :subpage "plan"})}
                "Add a plan"]]))]

     ;; Tell them to upgrade when they're using more parallelism than their plan allows,
     ;; but only on the tiles between (allowed parallelism and their current parallelism]
     (when (and (> (:parallel project) (plan-model/usable-containers plan))
                (>= (:parallel project) parallelism)
                (> parallelism (plan-model/usable-containers plan)))
       [:div.insufficient-minimum
        "Unsupported. Upgrade or lower parallelism."
        [:i.fa.fa-question-circle {:title (str "You need " parallelism " containers on your plan to use "
                                               parallelism "x parallelism.")}]
        [:a {:href (routes/v1-org-settings-subpage {:org-id (:org_name plan)
                                                    :subpage "containers"})}
         "Upgrade"]]))))

(defn parallelism-picker [project-data controls-ch]
  [:div.parallelism-picker
   (if-not (:plan project-data)
     [:div.loading-spinner common/spinner]
     (let [plan (:plan project-data)
           project (:project project-data)
           project-id (project-model/id project)]
       (list
        (when (:parallelism-edited project-data)
          [:div.try-out-build
           (branch-picker project-data controls-ch :button-text (str "Try a build!"))])
        [:form.parallelism-items
         (for [parallelism (range 1 (max (plan-model/max-parallelism plan)
                                         (inc 24)))]
           ;; XXX do we need parallel focus in
           [:label {:class (parallel-label-classes project-data parallelism)
                    :for (str "parallel_input_" parallelism)}
            parallelism
            (parallelism-tile project-data parallelism)
            [:input {:id (str "parallel_input_" parallelism)
                     :type "radio"
                     :name "parallel"
                     :value parallelism
                     :on-click #(put! controls-ch [:selected-project-parallelism
                                                   {:project-id project-id
                                                    :parallelism parallelism}])
                     :disabled (> parallelism (plan-model/max-selectable-parallelism plan))
                     :checked (= parallelism (:parallel project))}]])])))])

(defn parallel-builds [project-data controls-ch]
  [:div
   [:h2 (str "Change parallelism for " (vcs-url/project-name (get-in project-data [:project :vcs_url])))]
   (if-not (:plan project-data)
     [:div.loading-spinner common/spinner]
     (list (parallelism-picker project-data controls-ch)
           (mini-parallelism-faq project-data controls-ch)))])

(defn env-vars [project-data controls-ch]
  (let [project (:project project-data)
        new-env-var-name (:new-env-var-name project-data)
        new-env-var-value (:new-env-var-value project-data)
        project-id (project-model/id project)]
    [:div.environment-variables
     [:h2 "Environment variables for " (vcs-url/project-name (:vcs_url project))]
     [:div.environment-variables-inner
      [:p
       "Add environment variables to the project build.  You can add sensitive data (e.g. API keys) here, rather than placing them in the repository. "
       "The values can be any bash expression and can reference other variables, such as setting "
       [:code "M2_MAVEN"] " to " [:code "${HOME}/.m2)"] "."
       "To disable string substitution you need to escape the " [:code "$"]
       " characters by prefixing them with " [:code "\\"] "."
       "For example a crypt'ed password like " [:code "$1$O3JMY.Tw$AdLnLjQ/5jXF9.MTp3gHv/"]
       " you would enter " [:code "\\$1\\$O3JMY.Tw\\$AdLnLjQ/5jXF9.MTp3gHv/"] "."]
      [:form {:on-submit #(do (put! controls-ch [:created-env-var {:project-id project-id
                                                                   :env-var {:name new-env-var-name
                                                                             :value new-env-var-value}}])
                              false)}
       [:input#env-var-name
        {:required true, :type "text", :value new-env-var-name
         :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-env-var-name) %)}]
       [:label {:placeholder "Name"}]
       [:input#env-var-value
        {:required true, :type "text", :value new-env-var-value
         :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-env-var-value) %)}]
       [:label {:placeholder "Value"}]
       [:input {:data-failed-text "Failed",
                :data-success-text "Added",
                :data-loading-text "Adding...",
                :value "Save variables",
                :type "submit"}]]
      (when-let [env-vars (seq (:envvars project-data))]
        [:table
         [:thead [:tr [:th "Name"] [:th "Value"] [:th]]]
         [:tbody
          (for [{:keys [name value]} env-vars]
            [:tr
             [:td name]
             [:td value]
             [:td
              [:a
               {:title "Remove this variable?",
                :on-click #(put! controls-ch [:deleted-env-var {:project-id project-id
                                                                :env-var-name name}])}
               [:i.fa.fa-times-circle]
               [:span " Remove"]]]])]])]]))

(defn dependencies [project-data controls-ch]
  (let [project (:project project-data)
        project-id (project-model/id project)
        {:keys [setup dependencies post_dependencies]} project]
    [:div.dependencies-page
     [:h2 "Install dependencies for " (vcs-url/project-name (:vcs_url project))]
     [:div.dependencies-inner
      [:form.spec_form
       [:fieldset
        [:textarea {:name "setup",
                    :required true
                    :value setup
                    :on-change #(utils/edit-input controls-ch (conj state/project-path :setup) %)}]
        [:label {:placeholder "Pre-dependency commands"}]
        [:p "Run extra commands before the normal setup, these run before our inferred commands. All commands are arbitrary bash statements, and run on Ubuntu 12.04. Use this to install and setup unusual services, such as specific DNS provisions, connections to a private services, etc."]
        [:textarea {:name "dependencies",
                    :required true
                    :value dependencies
                    :on-change #(utils/edit-input controls-ch (conj state/project-path :dependencies) %)}]
        [:label {:placeholder "Dependency overrides"}]
        [:p "Replace our inferred setup commands with your own bash commands. Dependency overrides run instead of our inferred commands for dependency installation. If our inferred commands are not to your liking, replace them here. Use this to override the specific pre-test commands we run, such as "
         [:code "bundle install"] ", " [:code "rvm use"] ", " [:code "ant build"] ", "
         [:code "configure"] ", " [:code "make"] ", etc."]
        [:textarea {:required true
                    :value post_dependencies
                    :on-change #(utils/edit-input controls-ch (conj state/projet-path :post_dependencies) %)}]
        [:label {:placeholder "Post-dependency commands"}]
        [:p "Run extra commands after the normal setup, these run after our inferred commands for dependency installation. Use this to run commands that rely on the installed dependencies."]
        [:input {:value "Next, setup your tests",
                 :type "submit"
                 :on-click #(do (put! controls-ch [:saved-dependencies-commands
                                                   {:project-id project-id
                                                    :settings {:setup setup
                                                               :dependencies dependencies
                                                               :post_dependencies post_dependencies}}])
                                false)}]]]]]))

(defn tests [project-data controls-ch]
  (let [project (:project project-data)
        project-id (project-model/id project)
        {:keys [test extra]} project]
    [:div.tests-page
     [:h2 "Set up tests for " (vcs-url/project-name (:vcs_url project))]
     [:div.tests-inner
      [:fieldset.spec_form
       [:textarea {:name "test",
                   :required true
                   :value test
                   :on-change #(utils/edit-input controls-ch (conj state/project-path :test) %)}]
       [:label {:placeholder "Test commands"}]
       [:p "Replace our inferred test commands with your own inferred commands. These test commands run instead of our inferred test commands. If our inferred commands are not to your liking, replace them here. As usual, all commands are arbitrary bash, and run on Ubuntu 12.04."]
       [:textarea {:name "extra",
                   :required true
                   :value extra
                   :on-change #(utils/edit-input controls-ch (conj state/project-path :extra) %)}]
       [:label {:placeholder "Post-test commands"}]
       [:p "Run extra test commands after the others finish. Extra test commands run after our inferred commands. Add extra tests that we haven't thought of yet."]
       [:input {:name "save",
                :data-loading-text "Saving...",
                :value "Save commands",
                :type "submit"
                :on-click #(do (put! controls-ch [:saved-test-commands
                                                  {:project-id project-id
                                                   :settings {:test test
                                                              :extra extra}}])
                               false)}]
       [:div.try-out-build
        (branch-picker project-data controls-ch
                       :button-text "Save & Go!"
                       :channel-message :saved-test-commands-and-build
                       :channel-args {:project-id project-id
                                      :settings {:test test
                                                 :extra extra}})]]]]))

(defn fixed-failed-input [project controls-ch field]
  (let [notify_pref (get project field)
        id (string/replace (name field) "_" "-")]
    [:label {:for id}
     [:input {:id id
              :checked (= "smart" notify_pref)
              :on-change #(utils/edit-input controls-ch (conj state/project-path field) %
                                            :value (if (= "smart" notify_pref) nil "smart"))
              :value "smart"
              :type "checkbox"}]
     [:span "Fixed/Failed Only"]
     [:i.fa.fa-question-circle
      {:data-bind "tooltip: {}",
       :title
       "Only send notifications for builds that fail or fix the tests. Otherwise, send a notification for every build."}]]))

(defn chatroom-item [project controls-ch {:keys [service icon doc inputs show-fixed-failed?
                                                 top-section-content]}]
  [:div.chat-room-item
   [:div.chat-room-head [:h4 {:class icon} service]]
   [:div.chat-room-body
    [:section
     doc
     top-section-content]
    [:section
     (for [{:keys [field placeholder]} inputs]
       (list
        [:input {:id (string/replace (name field) "_" "-") :required true :type "text"
                 :value (get project field)
                 :on-change #(utils/edit-input controls-ch (conj state/project-path field) %)}]
        [:label {:placeholder placeholder}]))]]
   [:div.chat-room-foot
    (when show-fixed-failed?
      (fixed-failed-input project controls-ch :hipchat_notify_prefs))]])

(defn chatrooms [project-data controls-ch]
  (let [project (:project project-data)
        project-id (project-model/id project)]
    [:div
     [:h2 "Chatroom setup for" (vcs-url/project-name (:vcs_url project))]
     [:div.chat-rooms
      (for [chat-spec [{:service "Hipchat"
                        :icon "chat-i-hip"
                        :doc (list [:p "To get your API token, create a \"notification\" token via the "
                                    [:a {:href "https://hipchat.com/admin/api"} "HipChat site"] "."]
                                   [:label ;; hipchat is a special flower
                                    {:for "hipchat-notify"}
                                    [:input#hipchat-notify
                                     {:type "checkbox"
                                      :checked (:hipchat_notify project)
                                      :on-change #(utils/toggle-input controls-ch (conj state/project-path :hipchat_notify) %)}]
                                    [:span "Show popups"]])
                        :inputs [{:field :hipchat_room :placeholder "Room"}
                                 {:field :hipchat_api_token :placeholder "API"}]
                        :show-fixed-failed? true}

                       {:service "Campfire"
                        :icon "chat-i-camp"
                        :doc [:p "To get your API token, visit your company Campfire, then click \"My info\". Note that if you use your personal API token, campfire won't show the notifications to you!"]
                        :inputs [{:field :campfire_room :placeholder "Room"}
                                 {:field :campfire_subdomain :placeholder "Subdomain"}
                                 {:field :campfire_token :placeholder "API"}]
                        :show-fixed-failed? true}

                       {:service "Flowdock"
                        :icon "chat-i-flow"
                        :doc [:p "To get your API token, visit your Flowdock, then click the \"Settings\" icon on the left. On the settings tab, click \"Team Inbox\""]
                        :inputs [{:field :flowdock_api_token :placeholder "API"}]
                        :show-fixed-failed? false}

                       {:service "IRC"
                        :icon "chat-i-flow"
                        :doc nil
                        :inputs [{:field :irc_server :placeholder "Hostname"}
                                 {:field :irc_channel :placeholder "Channel"}
                                 {:field :irc_keyword :placeholder "Private Keyword"}
                                 {:field :irc_username :placeholder "Username"}
                                 {:field :irc_password :placeholder "Password (optional)"}]
                        :show-fixed-failed? true}

                       {:service "Slack"
                        :icon "chat-i-slack"
                        :doc [:p "To get your Webhook URL, visit Slack's "
                              [:a {:href "https://my.slack.com/services/new/circleci"}
                               "CircleCI Integration"]
                              " page, choose a default channel, and click the green \"Add CircleCI Integration\" button at the bottom of the page."]
                        :inputs [{:field :slack_webhook_url :placeholder "Webhook URL"}]
                        :show-fixed-failed? true}

                       {:service "Hall"
                        :icon "chat-i-hall"
                        :doc [:p "To get your Room / Group API token, go to "
                              [:strong "Settings > Integrations > CircleCI"]
                              " from within your Hall Group."]
                        :inputs [{:field :hall_room_api_token :placeholder "API"}]
                        :show-fixed-failed? true}]]
        (chatroom-item project controls-ch chat-spec))]
     [:div.chat-room-save
      [:input
       {:data-success-text "Saved",
        :data-loading-text "Saving",
        :value "Save notification hooks",
        :type "submit",
        :on-click #(do (put! controls-ch [:saved-notification-hooks {:project-id project-id}]))}]]]))

(defn webhooks [project-data controls-ch]
  [:div
   [:h2 "Webhooks for " (vcs-url/project-name (get-in project-data [:project :vcs_url]))]
   [:div.doc
    [:p
     "Circle also support webhooks, which run at the end of a build. They can be configured in your "
     [:a {:href "https://circleci.com/docs/configuration#notify" :target "_blank"}
      "circle.yml file"] "."]]])

(defn ssh-keys [project-data controls-ch]
  (let [project (:project project-data)
        project-id (project-model/id project)
        {:keys [hostname public-key private-key]
         :or {hostname "" public-key "" private-key ""}} (:new-ssh-key project-data)]
    [:div.sshkeys-page
     [:h2 "SSH keys for " (vcs-url/project-name (:vcs_url project))]
     [:div.sshkeys-inner
      [:p "Add keys to the build VMs that you need to deploy to your machines. If the hostname field is blank, the key will be used for all hosts."]
      [:form
       [:input#hostname {:required true, :type "text" :value hostname
                         :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-ssh-key :hostname) %)}]
       [:label {:placeholder "Hostname"}]
       [:input#publicKey {:required true, :type "text" :value public-key
                          :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-ssh-key :public-key) %)}]
       [:label {:placeholder "Public Key"}]
       [:textarea#privateKey {:required true :value private-key
                              :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-ssh-key :private-key) %)}]
       [:label {:placeholder "Private Key"}]
       [:input#submit.btn
        {:data-failed-text "Failed",
         :data-success-text "Saved",
         :data-loading-text "Saving..",
         :value "Submit",
         :type "submit"
         :on-click #(do (put! controls-ch [:saved-ssh-key {:project-id project-id
                                                           :ssh-key {:hostname hostname
                                                                     :public_key public-key
                                                                     :private_key private-key}}])
                        false)}]]
      (when-let [ssh-keys (seq (:ssh_keys project))]
        [:table
         [:thead [:tr [:th "Hostname"] [:th "Fingerprint"] [:th]]]
         [:tbody
          (for [{:keys [hostname fingerprint]} ssh-keys]
            [:tr
             [:td hostname]
             [:td fingerprint]
             [:td [:a {:title "Remove this Key?",
                       :on-click #(put! controls-ch [:deleted-ssh-key {:project-id project-id
                                                                       :fingerprint fingerprint}])}
                   [:i.fa.fa-times-circle]
                   [:span " Remove"]]]])]])]]))

(defn github-user [project-data controls-ch]
  [:div "Wait for "
   [:a {:href "https://github.com/circleci/circle/pull/2259"}
    "#2259"]
   " to merge."])

(defn api-tokens [project-data controls-ch]
  (let [project (:project project-data)
        project-id (project-model/id project)
        {:keys [scope label]
         :or {scope "status" label ""}} (:new-api-token project-data)]
    [:div.circle-api-page
     [:h2 "API tokens for " (vcs-url/project-name (:vcs_url project))]
     [:div.circle-api-page-inner
      [:p "Create and revoke project-specific API tokens to access this project's details using ourAPI. First choose a scope "
       ;; XXX popovers
       [:i.fa.fa-question-circle {:data-bind "popover"}]
       " and then create a label."]
      [:form
       [:div.styled-select
        [:select {:name "scope" :value scope
                  :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-api-token :scope) %)}
         [:option {:value "status"} "Status"]
         [:option {:value "all"} "All"]]
        [:i.fa.fa-chevron-down]]
       [:input
        {:required true, :type "text" :value label
         :on-change #(utils/edit-input controls-ch (conj state/project-data-path :new-api-token :label) %)}]
       [:label {:placeholder "Token label"}]
       [:input
        {:data-failed-text "Failed",
         :data-success-text "Created",
         :data-loading-text "Creating...",
         :on-click #(do (put! controls-ch [:saved-project-api-token {:project-id project-id
                                                                     :api-token {:scope scope
                                                                                 :label label}}])
                        false)
         :value "Create token",
         :type "submit"}]]
      (when-let [tokens (seq (:tokens project-data))]
        [:table
         [:thead
          [:th "Scope"]
          [:th "Label"]
          [:th "Token"]
          [:th "Created"]
          [:th]]
         [:tbody
          (for [{:keys [scope label token time]} tokens]
            [:tr
             [:td scope]
             [:td label]
             [:td [:span.code token]]
             [:td time]
             [:td
              [:a.slideBtn
               {:title "Remove this Key?",
                :on-click #(put! controls-ch [:deleted-project-api-token {:project-id project-id
                                                                          :token token}])}
               [:i.fa.fa-times-circle]
               [:span " Remove"]]]])]])]]))

(defn artifacts [project-data controls-ch]
 [:div
  [:h2 "Build artifacts for " (vcs-url/project-name (get-in project-data [:project :vcs_url]))]
  [:div.doc
   [:p
    "Circle supports saving files from any build. See "
    [:a {:href "/docs/build-artifacts", :target "_blank"}
     "our build artifact documentation‘"]
    " to set it up."]]])

(defn heroku [user project-data controls-ch]
  (let [project (:project project-data)
        project-id (project-model/id project)
        login (:login user)]
    [:div.heroku-api
     [:h2 "Set personal Heroku API key for " (vcs-url/project-name (:vcs_url project))]
     [:div.heroku-step
      [:h4 "Step 1: Heroku API key"]
      [:div (when (:heroku_api_key user)
              [:p "Your Heroku key is entered. Great!"])
       [:p (:heroku_api_key user)]
       [:div (when-not (:heroku_api_key user)
               ;; XXX pull in set heroku-key from accounts
               (comment (frontend.components.account/heroku user controls-ch)))]
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
         [:input.remove-user
          {:data-success-text "Saved",
           :data-loading-text "Saving...",
           :on-click #(do (put! controls-ch [:removed-heroku-deploy-user {:project-id project-id}])
                          false)
           :value "Remove Heroku Deploy User",
           :type "submit"}]

         [:input.set-user
          {:data-success-text "Saved",
           :data-loading-text "Saving...",
           :on-click #(do (put! controls-ch [:set-heroku-deploy-user {:project-id project-id
                                                                      :login login}])
                          false)
           :value (str "Set user to " (:login user)),
           :type "submit"}])]]
     [:div.heroku-step
      [:h4
       "Step 3: Add deployment settings to your "
       [:a {:href "/docs/configuration#deployment"} "circle.yml file"] " (example below)."]
      [:pre
       [:code
        "deployment:\n"
        "  staging:\n"
        "    branch: master\n"
        "    heroku:\n"
        "      appname: foo-bar-123"]]]]))

(defn other-deployment [project-data controls-ch]
  [:div
   [:h2
    "Other deployments for " (vcs-url/project-name (get-in project-data [:project :vcs_url]))]
   [:div.doc
    [:p "Circle supports deploying to any server, using custom commands. See "
     [:a {:target "_blank",
          :href "https://circleci.com/docs/configuration#deployment"}
      "our deployment documentation"]
     " to set it up."]]])

(defn subpage-fn [subpage user]
  (get {:parallel-builds parallel-builds
        :env-vars env-vars
        :setup dependencies
        :tests tests
        :hooks chatrooms
        :webhooks webhooks
        :ssh ssh-keys
        :github github-user
        :api api-tokens
        :artifacts artifacts
        :heroku (partial heroku user)
        :deployment other-deployment}
       subpage
       overview))

(defn follow-sidebar [project controls-ch]
  (let [project-id (project-model/id project)
        vcs-url (:vcs_url project)]
    [:div.follow-status
     [:div.followed
      ;; this is weird, but it's what the css expectss
      (when (:followed project)
        (list
         [:i.fa.fa-group]
         [:h4 "You're following this repo"]
         [:p
          "We'll keep an eye on this and update you with personalized build emails. "
          "You can stop these any time from your "
          [:a {:href "/account"} "account settings"]
          "."]
         ;; XXX make unfollow work!
         [:button {:on-click #(put! controls-ch [:unfollowed-repo {:vcs_url vcs-url}])}
          "Unfollow"]))]
     [:div.not-followed
      (when-not (:followed project)
        (list
         [:h4 "You're not following this repo"]
         [:p
          "We can't update you with personalized build emails unless you follow this project. "
          "Projects are only tested if they have a follower."]
         [:button {:on-click #(put! controls-ch [:followed-repo {:vcs_url vcs-url}])}
          "Follow"]))]]))

(defn project-settings [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [project-data (get-in data state/project-data-path)
            user (:current-user data)
            subpage (:project-settings-subpage data)
            controls-ch (get-in opts [:comms :controls])]
        (html
         (if-not (get-in project-data [:project :vcs_url]) ; wait for project-settings to load
           [:div.loading-spinner common/spinner]
           [:div#project-settings
            [:aside sidebar]
            [:div.project-settings-inner
             (common/flashes)
             [:div#subpage
              ((subpage-fn subpage user) project-data controls-ch)]]
            (follow-sidebar (:project project-data) controls-ch)]))))))
