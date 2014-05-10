(ns frontend.components.project-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.models.build :as build-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.components.common :as common]
            [frontend.routes :as routes]
            [frontend.utils :as utils]
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

(defn branch-picker [project settings controls-ch & {:keys [button-text]
                                                     :or {button-text "Start a build"}}]
  (let [project-id (project-model/id project)
        default-branch (:default_branch project)
        settings-branch (get-in settings [:projects project-id :settings-branch] default-branch)]
    [:form {:on-submit #(do (put! controls-ch [:started-edit-settings-build {:project-id project-id
                                                                             :branch settings-branch}])
                            false)}
     [:input {:name "branch"
              :required true
              :type "text"
              :value settings-branch
              ;; XXX typeahead
              :on-change #(put! controls-ch [:edit-project-settings-branch {:value (.. % -target -value)
                                                                            :project-id project-id}])}]
     [:label {:placeholder "Test settings on..."}]
     [:input
      {:value button-text
       ;; XXX handle data-loading-text
       :data-loading-text "Starting..."
       :type "submit"}]]))

(defn overview [project settings controls-ch]
  [:div.project-settings-block
   [:h2 "How to configure " [:span (vcs-url/project-name (:vcs_url project))]]
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

(defn mini-parallelism-faq [project settings controls-ch]
  [:div.mini-faq
   [:div.mini-faq-item
    [:h3 "What are containers?"]
    [:p
     "Containers are what we call the virtual machines that your tests run in. Your current plan has "
     (get-in project [:plan :containers])
     " containers and supports up to "
     (plan-model/max-parallelism (:plan project))
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

(defn parallel-label-classes [project parallelism]
  (concat
   []
   (when (> parallelism (plan-model/max-selectable-parallelism (:plan project))) ["disabled"])
   (when (= parallelism (:parallel project)) ["selected"])
   (when (not= 0 (mod (plan-model/usable-containers (:plan project)) parallelism)) ["bad_choice"])))

(defn parallelism-tile
  "Determines what we show when they hover over the parallelism option"
  [project parallelism]
  (let [plan (:plan project)
        project-id (project-model/id project)]
    (list
     [:div.parallelism-upgrades
      (if-not (= "trial" (get-in project [:plan :type]))
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

(defn parallelism-picker [project settings controls-ch]
  [:div.parallelism-picker
   (if-not (:plan project)
     [:div.loading-spinner common/spinner]
     (let [plan (:plan project)
           project-id (project-model/id project)]
       (list
        (when (:parallelism-edited project)
          [:div.try-out-build
           (branch-picker project settings controls-ch :button-text (str "Try a build!"))])
        [:form.parallelism-items
         (for [parallelism (range 1 (max (plan-model/max-parallelism plan)
                                         (inc 24)))]
           ;; XXX do we need parallel focus in
           [:label {:class (parallel-label-classes project parallelism)
                    :for (str "parallel_input_" parallelism)}
            parallelism
            (parallelism-tile project parallelism)
            [:input {:id (str "parallel_input_" parallelism)
                     :type "radio"
                     :name "parallel"
                     :value parallelism
                     :on-click #(put! controls-ch [:selected-project-parallelism
                                                   {:project-id project-id
                                                    :parallelism parallelism}])
                     :disabled (> parallelism (plan-model/max-selectable-parallelism plan))
                     :checked (= parallelism (:parallel project))}]])])))])

(defn parallel-builds [project settings controls-ch]
  [:div
   [:h2 (str "Change parallelism for " (vcs-url/project-name (:vcs_url project)))]
   (if-not (:plan project)
     [:div.loading-spinner common/spinner]
     (list (parallelism-picker project settings controls-ch)
           (mini-parallelism-faq project settings controls-ch)))])

(defn env-vars [project settings controls-ch]
  (let [new-env-var-name (:new-env-var-name project)
        new-env-var-value (:new-env-var-value project)
        project-id (project-model/id project)]
    [:div.environment-variables
     [:h2 "Environment variables for "  (vcs-url/project-name (:vcs_url project))]
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
         :on-change #(put! controls-ch [:edited-new-env-var-name {:project-id project-id
                                                                  :value (.. % -target -value)}])}]
       [:label {:placeholder "Name"}]
       [:input#env-var-value
        {:required true, :type "text", :value new-env-var-value
         :on-change #(put! controls-ch [:edited-new-env-var-value {:project-id project-id
                                                                   :value (.. % -target -value)}])}]
       [:label {:placeholder "Value"}]
       [:input {:data-failed-text "Failed",
                :data-success-text "Added",
                :data-loading-text "Adding...",
                :value "Save variables",
                :type "submit"}]]
      (when-let [env-vars (seq (:env-vars project))]
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
                                                                :env-var-name name
                                                                :env-var-value value}])}
               [:i.fa.fa-times-circle]
               [:span "Remove"]]]])]])]]))

(defn dependencies [project settings controls-ch]
  [:div "deps"])

(defn tests [project settings controls-ch]
  [:div "tests"])

(defn chatrooms [project settings controls-ch]
  [:div "chatrooms"])

(defn webhooks [project settings controls-ch]
  [:div "webhooks"])

(defn ssh-keys [project settings controls-ch]
  [:div "ssh-keys"])

(defn github-user [project settings controls-ch]
  [:div "github-user"])

(defn api-tokens [project settings controls-ch]
  [:div "api tokesn"])

(defn artifacts [project settings controls-ch]
  [:div "artifacts"])

(defn heroku [project settings controls-ch]
  [:div "heroku"])

(defn other-deployment [project settings controls-ch]
  [:div "deployment"])

(defn subpage-fn [subpage]
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
        :heroku heroku
        :deployment other-deployment}
       subpage
       overview))

(defn follow-sidebar [project settings controls-ch]
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
         [:button {:on-click #(put! controls-ch [:unfollowed-repo project-id])}
          "Unfollow"]))]
     [:div.not-followed
      (when-not (:followed project)
        (list
         [:h4 "You're not following this repo"]
         [:p
          "We can't update you with personalized build emails unless you follow this project. "
          "Projects are only tested if they have a follower."]
         [:button {:on-click #(put! controls-ch [:follow-repo {:vcs_url vcs-url}])}
          "Follow"]))]]))

(defn project-settings [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [project (:current-project data)
            settings (:settings data)
            subpage (:project-settings-subpage data)
            controls-ch (get-in opts [:comms :controls])]
        (html
         (if-not (:vcs_url project) ; wait for project-settings to load
           [:div.loading-spinner common/spinner]
           [:div#project-settings
            [:aside sidebar]
            [:div.project-settings-inner
             (common/flashes)
             [:div#subpage
              ((subpage-fn subpage) project settings controls-ch)]]
            (follow-sidebar project settings controls-ch)]))))))
