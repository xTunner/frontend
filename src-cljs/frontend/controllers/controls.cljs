(ns frontend.controllers.controls
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [cljs.reader :as reader]
            [clojure.set :as set]
            [frontend.analytics.core :as analytics]
            [frontend.analytics.utils :as analytics-utils]
            [frontend.analytics.track :as analytics-track]
            [frontend.api :as api]
            [frontend.async :refer [put!]]
            [frontend.api.path :as api-path]
            [frontend.components.forms :refer [release-button!]]
            [frontend.models.action :as action-model]
            [frontend.models.project :as project-model]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan]
            [frontend.intercom :as intercom]
            [frontend.support :as support]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.stripe :as stripe]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.launchdarkly :as launchdarkly]
            [frontend.utils.seq :refer [dissoc-in]]
            [frontend.utils.state :as state-utils]
            [goog.dom]
            [goog.string :as gstring]
            [goog.labs.userAgent.engine :as engine]
            goog.style
            [frontend.models.user :as user-model]
            [frontend.pusher :as pusher])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]])
  (:import [goog.fx.dom.Scroll]))

;; --- Helper Methods ---

(defn container-id [container]
  (int (last (re-find #"container_(\d+)" (.-id container)))))


(defn extract-from
  "Extract data from a nested map. Returns a new nested map comprising only the
  nested keys from `path`.

  user=> (extract-from nil nil)
  nil
  user=> (extract-from nil [])
  nil
  user=> (extract-from nil [:a])
  nil
  user=> (extract-from {} [:a])
  nil
  user=> (extract-from {:a 1} [:a])
  {:a 1}
  user=> (extract-from {:a {:b {:c 1}}, :d 2} [:a :b])
  {:a {:b {:c 1}}}"
  [m path]
  (when (seq path)
    (let [sentinel (js-obj)
          value (get-in m path sentinel)]
      (when-not (identical? value sentinel)
        (assoc-in {} path value)))))

(defn merge-settings
  "Merge new settings from inputs over a subset of project settings."
  [paths project settings]
  (letfn []
    (if (not (seq paths))
      settings
      (utils/deep-merge (apply merge {} (map (partial extract-from project) paths))
                        settings))))

(defn button-ajax
  "An ajax/ajax wrapper that releases the current managed-button after the API
  request.  Exists to faciliate migration away from stateful-button."
  [method url message channel & opts]
  (let [uuid frontend.async/*uuid*
        c (chan)
        events (-> (apply hash-map opts)
                   :events)]
    (apply ajax/ajax method url message c opts)
    (go-loop []
      (when-let [[_ status _ :as event] (<! c)]
        (when-let [event-handler (-> events status)]
          (event-handler))
        (when (#{:success :failed} status)
          (release-button! uuid status))
        (>! channel event)
        (recur)))))

(defn toggle-project
  "Toggle follow and unfollow project repos."
  [current-state vcs-url context control-event follow-path]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        vcs-type (vcs-url/vcs-type vcs-url)
        analytics-event (case control-event
                          :follow-repo :project-followed
                          :follow-project :project-followed
                          :unfollow-repo :project-unfollowed
                          :unfollow-project :project-unfollowed)]

      (button-ajax :post
                  (follow-path vcs-type project)
                  control-event
                  api-ch
                  :context context
                  :events {:success #(analytics/track {:event-type analytics-event
                                                       :current-state current-state
                                                       :properties {:org org-name
                                                                    :repo repo-name
                                                                    :vcs-type vcs-type}})})))

;; --- Navigation Multimethod Declarations ---

(defmulti control-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message args state] message))

(defmulti post-control-event!
  (fn [target message args previous-state current-state] message))

;; --- Navigation Multimethod Implementations ---

(defmethod control-event :default
  [target message args state]
  (utils/mlog "Unknown controls: " message)
  state)

(defmethod post-control-event! :default
  [target message args previous-state current-state]
  (utils/mlog "No post-control for: " message))


(defmethod control-event :user-menu-toggled
  [target message _ state]
  (update-in state [:settings :menus :user :open] not))


(defmethod control-event :show-all-branches-toggled
  [target message value state]
  (assoc-in state state/show-all-branches-path value))

(defmethod control-event :expand-repo-toggled
  [target message {:keys [repo]} state]
  (update-in state state/expanded-repos-path (fn [expanded-repos]
                                               ((if (expanded-repos repo)
                                                  disj
                                                  conj)
                                                expanded-repos repo))))

(defmethod control-event :sort-branches-toggled
  [target message value state]
  (assoc-in state state/sort-branches-by-recency-path value))

(defmethod control-event :collapse-branches-toggled
  [target message {:keys [collapse-group-id]} state]
  ;; Lets us store this in localstorage without leaking info about the user
  (update-in state (state/project-branches-collapsed-path collapse-group-id) not))

(defmethod control-event :collapse-build-diagnostics-toggled
  [target message {:keys [project-id-hash]} state]
  (update-in state (state/project-build-diagnostics-collapsed-path project-id-hash) not))

(defmethod control-event :show-admin-panel-toggled
  [target message _ state]
  (update-in state state/show-admin-panel-path not))

(defmethod control-event :instrumentation-line-items-toggled
  [target message _ state]
  (update-in state state/show-instrumentation-line-items-path not))

(defmethod control-event :clear-instrumentation-data-clicked
  [target message _ state]
  (assoc-in state state/instrumentation-path []))

(defmethod control-event :show-inspector-toggled
  [target message _ state]
  (update-in state state/show-inspector-path not))

(defmethod control-event :state-restored
  [target message path state]
  (let [str-data (.getItem js/sessionStorage "circle-state")]
    (if (seq str-data)
      (-> str-data
          reader/read-string
          (assoc :comms (:comms state)))
      state)))


(defmethod control-event :usage-queue-why-toggled
  [target message {:keys [build-id]} state]
  (update-in state state/show-usage-queue-path not))

(defmethod post-control-event! :usage-queue-why-showed
  [target message {:keys [username reponame
                          build_num build-id]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (api/get-usage-queue (get-in current-state state/build-path) api-ch)))

(defmethod control-event :selected-add-projects-org
  [target message args state]
  (-> state
      (assoc-in [:settings :add-projects :selected-org] args)
      (assoc-in [:settings :add-projects :repo-filter-string] "")
      (state-utils/reset-current-org)))

(defmethod post-control-event! :selected-add-projects-org
  [target message {:keys [vcs_type login]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when (user-model/has-org? (get-in current-state state/user-path) login vcs_type)
      (api/get-org-settings login vcs_type api-ch)
      (api/get-org-plan login vcs_type api-ch)))
  (utils/scroll-to-id! "project-listing"))

(defmethod post-control-event! :refreshed-user-orgs [target message args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (api/get-orgs api-ch :include-user? true)))

(defmethod post-control-event! :artifacts-showed
  [target message _ previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        build (get-in current-state state/build-path)
        vcs-type (vcs-url/vcs-type (:vcs_url build))
        project-name (vcs-url/project-name (:vcs_url build))
        build-num (:build_num build)]
    (ajax/ajax :get
               (api-path/artifacts vcs-type project-name build-num)
               :build-artifacts
               api-ch
               :context (build-model/id build))))

(defmethod post-control-event! :tests-showed
  [target message _ previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        build (get-in current-state state/build-path)]
    (when (empty? (get-in current-state state/tests-path))
      (api/get-build-tests build api-ch))))

(defmethod control-event :show-config-toggled
  [target message build-id state]
  (update-in state state/show-config-path not))

(defmethod control-event :container-selected
  [target message {:keys [container-id]} state]
  (assoc-in state state/current-container-path container-id))

(defmethod post-control-event! :container-selected
  [target message {:keys [container-id animate?] :or {animate? true}} previous-state current-state]
  (when-let [parent (goog.dom/getElement "container_parent")]
    (let [container (goog.dom/getElement (str "container_" container-id))
          body (.-body js/document)
          current-scroll-top (.-scrollTop parent)
          body-scroll-top (.-scrollTop body)
          current-scroll-left (.-scrollLeft parent)
          new-scroll-left (int (.-x (goog.style.getContainerOffsetToScrollInto container parent)))]
      (let [scroller (or (.-scroll_handler parent)
                         (set! (.-scroll_handler parent)
                               ;; Store this on the parent so that we
                               ;; don't handle parent scroll while the
                               ;; animation is playing
                               (goog.fx.dom.Scroll. parent
                                                    #js [0 0]
                                                    #js [0 0]
                                                    (if animate? 250 0))))
            onEnd (.-onEnd scroller)]
        (set! (.-startPoint scroller) #js [current-scroll-left 0])
        (set! (.-endPoint scroller) #js [new-scroll-left 0])
        ;; Browser find can scroll an absolutely positioned container into view,
        ;; causing the parent to scroll. But then we set it to relative and there
        ;; is no longer any overflow, so we need to scroll app-main instead.
        (set! (.-onEnd scroller) #(do (.call onEnd scroller)
                                      (set! (.-scrollTop body)
                                            (+ body-scroll-top current-scroll-top))))
        (.play scroller))))
  (let [previous-container-id (get-in previous-state state/current-container-path)]
    (when (not= previous-container-id container-id)
      (let [container (get-in current-state (state/container-path container-id))
            last-action (-> container :actions last)
            vcs-url (:vcs_url (get-in current-state state/build-path))]
        (api/get-action-steps {:vcs-url vcs-url
                               :build-num (:build_num (get-in current-state state/build-path))
                               :project-name (vcs-url/project-name vcs-url)
                               :old-container-id previous-container-id
                               :new-container-id container-id}
                              (get-in current-state [:comms :api]))))))

(defmethod control-event :container-paging-offset-changed
  [target message {:keys [paging-offset]} state]
  (assoc-in state state/container-paging-offset-path paging-offset))

(defmethod control-event :container-filter-changed
  [target message {:keys [new-filter containers]} state]
  (-> state
      (assoc-in state/current-container-filter-path new-filter)
      ;; A nil paging-offset means "display whatever page the selected container is on".
      (assoc-in state/container-paging-offset-path nil)))

(defmethod post-control-event! :container-filter-changed
  [target message {:keys [new-filter containers]} previous-state current-state]
  (let [selected-container-id (get-in current-state state/current-container-path)
        selected-container-in-containers? (some #(= selected-container-id (:index %)) containers)
        controls-ch (get-in current-state [:comms :controls])]
    (if-not (and selected-container-in-containers?
                 (seq containers))
      (put! controls-ch [:container-selected {:container-id (:index (first containers))
                                              :animate? true}]))))

(defmethod control-event :action-log-output-toggled
  [target message {:keys [index step value]} state]
  (assoc-in state (state/show-action-output-path index step) value))

(defmethod post-control-event! :action-log-output-toggled
  [target message {:keys [index step] :as args} previous-state current-state]
  (let [action (get-in current-state (state/action-path index step))
        build (get-in current-state state/build-path)]
    (when (and (action-model/visible? action (get-in current-state state/current-container-path))
               (not (:output action)))
      (api/get-action-output {:vcs-url (:vcs_url build)
                              :build-num (:build_num build)
                              :step step
                              :index index}
                             (get-in current-state [:comms :api])))))


(defmethod control-event :selected-project-parallelism
  [target message {:keys [project-id parallelism]} state]
  (assoc-in state (conj state/project-path :parallel) parallelism))

(defmethod post-control-event! :selected-project-parallelism
  [target message {:keys [project-id parallelism]} previous-state current-state]
  (when (not= (get-in previous-state state/project-path)
              (get-in current-state state/project-path))
    (let [previous-project (get-in previous-state state/project-path)
          new-project (get-in current-state state/project-path)
          api-ch (get-in current-state [:comms :api])
          project-name (vcs-url/project-name project-id)
          org-name (vcs-url/org-name project-id)
          repo-name (vcs-url/repo-name project-id)
          vcs-type (vcs-url/vcs-type project-id)]
      ;; TODO: edit project settings api call should respond with updated project settings
      (ajax/ajax :put
                 (api-path/project-settings vcs-type org-name repo-name)
                 :update-project-parallelism
                 api-ch
                 :params {:parallel parallelism}
                 :context {:project-id project-id})
    (analytics/track {:event-type :update-parallelism-clicked
                      :current-state current-state
                      :properties {:previous-parallelism (project-model/parallelism previous-project)
                                   :new-parallelism (project-model/parallelism new-project)
                                   :plan-type (analytics-utils/canonical-plan-type :paid)
                                   :vcs-type vcs-type}}))))

(defmethod post-control-event! :clear-cache
  [target message {:keys [type project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])
        uuid frontend.async/*uuid*]
    (ajax/ajax :delete
               (api-path/project-cache vcs-type project-name type)
               (case type
                 "build" :clear-build-cache
                 "source" :clear-source-cache)
               api-ch
               :context {:project-id project-id
                         :uuid uuid})))

(defmethod control-event :dismiss-result
  [target message result-path state]
  (update-in state (butlast result-path) dissoc (last result-path)))

(defmethod control-event :dismiss-invite-form
  [target message _ state]
  (assoc-in state state/dismiss-invite-form-path true))

(defmethod post-control-event! :dismiss-invite-form
  [_ _ _ _ current-state]
  (analytics/track {:event-type :invite-teammates-dismissed
                    :current-state current-state}))

(defmethod control-event :invite-selected-all
  [_ _ _ state]
  (update-in state state/invite-github-users-path (fn [users]
                                                    (vec (map #(assoc % :checked true) users)))))

(defmethod post-control-event! :invite-selected-all
  [_ _ _ _ current-state]
  (let [teammates (get-in current-state state/invite-github-users-path)]
    (analytics/track {:event-type :invite-teammates-select-all-clicked
                      :current-state current-state
                      :properties {:teammate-count (count teammates)}})))

(defmethod control-event :invite-selected-none
  [_ _ _ state]
  (update-in state state/invite-github-users-path (fn [users]
                                                    (vec (map #(assoc % :checked false) users)))))

(defmethod post-control-event! :invite-selected-none
  [_ _ _ _ current-state]
  (let [teammates (get-in current-state state/invite-github-users-path)]
    (analytics/track {:event-type :invite-teammates-select-none-clicked
                      :current-state current-state
                      :properties {:teammate-count (count teammates)}})))

(defmethod control-event :dismiss-config-errors
  [target message _ state]
  (assoc-in state state/dismiss-config-errors-path true))


(defmethod control-event :edited-input
  [target message {:keys [value path]} state]
  (assoc-in state path value))


(defmethod control-event :toggled-input
  [target message {:keys [path]} state]
  (update-in state path not))

(defmethod control-event :clear-inputs
  ;; assumes that paths are relative to inputs, e.g. [:new-env-var], not [:inputs :new-env-var]
  [target message {:keys [paths]} state]
  (reduce (fn [state path]
            (dissoc-in state (concat state/inputs-path path)))
          state paths))


(defmethod post-control-event! :support-dialog-raised
  [target message _ previous-state current-state]
  (support/raise-dialog (get-in current-state [:comms :errors])))


(defmethod post-control-event! :intercom-user-inspected
  [target message criteria previous-state current-state]
  (if-let [url (intercom/user-link)]
    (js/window.open url)
    (print "No matching url could be found from current window.location.pathname")))


(defmethod post-control-event! :state-persisted
  [target message channel-id previous-state current-state]
  (.setItem js/sessionStorage "circle-state"
            (pr-str (dissoc current-state :comms))))


(defmethod post-control-event! :retry-build-clicked
  [target message {:keys [build-num build-id vcs-url no-cache?] :as args} previous-state current-state]
  (let [api-ch (-> current-state :comms :api)
        uuid frontend.async/*uuid*
        vcs-type (vcs-url/vcs-type vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (go
      (let [api-result (<! (ajax/managed-ajax :post (api-path/build-retry vcs-type org-name repo-name build-num)
                                              :params (when no-cache? {:no-cache true})))]
        (put! api-ch [:retry-build (:status api-result) api-result])
        (release-button! uuid (:status api-result))
        (when (= :success (:status api-result))
          (analytics/track {:event-type :build-triggered
                            :current-state current-state
                            :build (:resp api-result)
                            :properties {:no-cache? no-cache?}}))))))


(defmethod post-control-event! :ssh-build-clicked
  [target message {:keys [build-num build-id vcs-url] :as args} previous-state current-state]
  (let [api-ch (-> current-state :comms :api)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        vcs-type (vcs-url/vcs-type vcs-url)
        uuid frontend.async/*uuid*]
    (go
     (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1.1/project/%s/%s/%s/%s/ssh" vcs-type org-name repo-name build-num)))]
       (put! api-ch [:retry-build (:status api-result) api-result])
       (release-button! uuid (:status api-result))
       (when (= :success (:status api-result))
         (analytics/track {:event-type :build-triggered
                           :current-state current-state
                           :build (:resp api-result)
                           :properties {:ssh? true}}))))))

(defmethod post-control-event! :ssh-current-build-clicked
  [target message {:keys [build-num vcs-url]} previous-state current-state]
  (let [api-ch (-> current-state :comms :api)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        vcs-type (vcs-url/vcs-type vcs-url)
        uuid frontend.async/*uuid*]
    (go
      (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1.1/project/%s/%s/%s/%s/ssh-users" vcs-type org-name repo-name build-num)))]
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :followed-repo
  [target message repo previous-state current-state]
  (toggle-project current-state (:vcs_url repo) repo 
                  :follow-repo api-path/project-follow))

(defmethod control-event :inaccessible-org-toggled
  [target message {:keys [org-name value]} state]
  (assoc-in state [:settings :add-projects :inaccessible-orgs org-name :visible?] value))


(defmethod post-control-event! :followed-project
  [target message {:keys [vcs-url project-id]} previous-state current-state]
  (toggle-project current-state vcs-url {:project-id project-id}
                  :follow-project api-path/project-follow))


(defmethod post-control-event! :unfollowed-repo
  [target message repo previous-state current-state]
  (toggle-project current-state (:vcs_url repo) repo
                  :unfollow-repo api-path/project-unfollow))


(defmethod post-control-event! :unfollowed-project
  [target message {:keys [vcs-url project-id] :as repo} previous-state current-state]
  (toggle-project current-state vcs-url {:project-id project-id}
                  :unfollow-project api-path/project-unfollow))

(defmethod post-control-event! :stopped-building-project
  [target message {:keys [vcs-url project-id]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        vcs-type (vcs-url/vcs-type vcs-url)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :delete
                 (api-path/project-enable vcs-type project)
                 :stop-building-project
                 api-ch
                 :context {:project-id project-id})
    :events {:success #(analytics/track {:event-type :project-builds-stopped
                                         :current-state current-state
                                         :properties {:org org-name
                                                      :repo repo-name}})}))

;; XXX: clean this up
(defmethod post-control-event! :container-parent-scroll
  [target message _ previous-state current-state]
  (let [controls-ch (get-in current-state [:comms :controls])
        current-container-id (get-in current-state state/current-container-path 0)
        parent (goog.dom/getElement "container_parent")
        parent-scroll-left (.-scrollLeft parent)
        current-container (goog.dom/getElement (str "container_" current-container-id))
        current-container-scroll-left (int (.-x (goog.style.getContainerOffsetToScrollInto current-container parent)))
        ;; XXX stop making (count containers) queries on each scroll
        containers (sort-by (fn [c] (Math/abs (- parent-scroll-left (.-x (goog.style.getContainerOffsetToScrollInto c parent)))))
                            (utils/node-list->seqable (goog.dom/getElementsByClass "container-view" parent)))
        new-scrolled-container-id (if (= parent-scroll-left current-container-scroll-left)
                                    current-container-id
                                    (if-not (engine/isGecko)
                                      ;; Safari and Chrome scroll the found content to the center of the page
                                      (container-id (first containers))
                                      ;; Firefox scrolls the content just into view
                                      ;; if we're scrolling left, then we want the container whose rightmost portion is showing
                                      ;; if we're scrolling right, then we want the container whose leftmost portion is showing
                                      (if (< parent-scroll-left current-container-scroll-left)
                                        (apply min (map container-id (take 2 containers)))
                                        (apply max (map container-id (take 2 containers))))))]
    ;; This is kind of dangerous, we could end up with an infinite loop. Might want to
    ;; do a swap here (or find a better way to structure this!)
    (when (not= current-container-id new-scrolled-container-id)
      (put! controls-ch [:container-selected {:container-id new-scrolled-container-id
                                              :animate? false}]))))


(defmethod post-control-event! :started-edit-settings-build
  [target message {:keys [project-id]} previous-state current-state]
  (let [repo-name (vcs-url/repo-name project-id)
        org-name (vcs-url/org-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        uuid frontend.async/*uuid*
        comms (get-in current-state [:comms])
        default-branch (get-in current-state state/project-default-branch-path)
        branch (get-in current-state state/input-settings-branch-path default-branch)]
    ;; TODO: edit project settings api call should respond with updated project settings
    (go
     (let [api-result (<! (ajax/managed-ajax :post (api-path/branch-path vcs-type org-name repo-name branch)))]
       (put! (:api comms) [:start-build (:status api-result) api-result])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :created-env-var
  [target message {:keys [project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (gstring/format "/api/v1.1/project/%s/%s/envvar" vcs-type project-name)
                 :create-env-var
                 api-ch
                 :params {:name (get-in current-state (conj state/inputs-path :new-env-var-name))
                          :value (get-in current-state (conj state/inputs-path :new-env-var-value))}
                 :context {:project-id project-id})))


(defmethod post-control-event! :deleted-env-var
  [target message {:keys [project-id env-var-name]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (ajax/ajax :delete
               (gstring/format "/api/v1.1/project/%s/%s/envvar/%s" vcs-type project-name env-var-name)
               :delete-env-var
               api-ch
               :context {:project-id project-id
                         :env-var-name env-var-name})))


(defmethod post-control-event! :saved-dependencies-commands
  [target message {:keys [project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        settings (state-utils/merge-inputs (get-in current-state state/projects-path)
                                           (get-in current-state state/inputs-path)
                                           [:setup :dependencies :post_dependencies])
        org (vcs-url/org-name project-id)
        repo (vcs-url/repo-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        uuid frontend.async/*uuid*
        comms (get-in current-state [:comms])]
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :put (api-path/project-settings vcs-type org repo)
                             :params settings))]
       (if (= :success (:status api-result))
         (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/project-settings vcs-type org repo)))]
           (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
           (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}])
           (put! (:nav comms) [:navigate! {:path (routes/v1-project-settings-path {:org org :repo repo :vcs_type vcs-type :_fragment "tests"})}]))
         (put! (:errors comms) [:api-error api-result]))
       (release-button! uuid (:status api-result))))))


(defmethod post-control-event! :saved-test-commands
  [target message {:keys [project-id start-build?]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        project (get-in current-state state/project-path)
        inputs (get-in current-state state/inputs-path)
        settings (state-utils/merge-inputs project inputs [:test :extra])
        branch (get inputs :settings-branch (:default_branch project))
        vcs-type (vcs-url/vcs-type project-id)
        org (vcs-url/org-name project-id)
        repo (vcs-url/repo-name project-id)
        uuid frontend.async/*uuid*
        comms (get-in current-state [:comms])]
    (go
     (let [api-result (<! (ajax/managed-ajax :put (api-path/project-settings vcs-type org repo) :params settings))]
       (if (= :success (:status api-result))
         (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/project-settings vcs-type org repo)))]
           (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
           (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}])
           (when start-build?
             (let [build-api-result (<! (ajax/managed-ajax :post (api-path/branch-path vcs-type org repo branch)))]
               (put! (:api comms) [:start-build (:status build-api-result) build-api-result]))))
         (put! (:errors comms) [:api-error api-result]))
       (release-button! uuid (:status api-result))))))

(defn save-project-settings
  "Takes the state of project settings inputs and PUTs the new settings to
  /api/v1/project/:project/settings.

  `merge-paths` is a list of paths into the nested project data-structure.
  When a merge-path is non-nil the part of the project data-structure at
  that path is used as the base values for the settings. The new settings
  from the inputs state are merged on top.

  This allows all the settings on a page to be submitted, even if the user only
  modifies one.

  E.g.
  project is
    {:github-info { ... }
     :aws {:keypair {:access_key_id \"access key\"
                     :secret_access_key \"secret key\"}}}

  The user sets a new access key ID so inputs is
    {:aws {:keypair {:access_key_id \"new key id\"}}}

  :merge-paths is [[:aws :keypair]]

  The settings posted to the settings API will be:
    {:aws {:keypair {:access_key_id \"new key id\"
                     :secret_access_key \"secret key\"}}}"
  [project-id merge-paths current-state]
  (let [project-name (vcs-url/project-name project-id)
        comms (get-in current-state [:comms])
        inputs (get-in current-state state/inputs-path)
        project (get-in current-state state/project-path)
        settings (merge-settings merge-paths project inputs)
        org-name (vcs-url/org-name project-id)
        repo-name (vcs-url/repo-name project-id)
        vcs-type (vcs-url/vcs-type project-id)]

    (go
      (let [api-result (<! (ajax/managed-ajax :put (api-path/project-settings vcs-type org-name repo-name) :params settings))]
        (if (= :success (:status api-result))
          (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/project-settings vcs-type org-name repo-name)))]
            (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
            (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}]))
          (put! (:errors comms) [:api-error api-result]))
        api-result))))

(defmethod control-event :selected-piggieback-orgs-updated
  [_ _ {:keys [org selected?]} state]
  (update-in state
             state/selected-piggieback-orgs-path
             (if selected? conj disj)
             org))

(defmethod control-event :selected-transfer-org-updated
  [_ _ {:keys [org]} state]
  (assoc-in state state/selected-transfer-org-path org))

(defmethod post-control-event! :saved-project-settings
  [target message {:keys [project-id merge-paths]} previous-state current-state]
  (let [uuid frontend.async/*uuid*]
    (go
      (let [api-result (<! (save-project-settings project-id merge-paths current-state))]
        (release-button! uuid (:status api-result))))))


(defmethod control-event :new-codedeploy-app-name-entered
  [target message _ state]
  (let [app-name (get-in state (conj state/inputs-path :project-settings-codedeploy-app-name))]
    (if (seq app-name)
      (-> state
          (update-in (conj state/project-path :aws :services :codedeploy) assoc app-name {})
          (assoc (conj state/inputs-path :project-settings-codedeploy-app-name) nil))
      state)))


(defmethod post-control-event! :saved-ssh-key
  [target message {:keys [project-id ssh-key]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (api-path/project-ssh-key vcs-type project-name)
                 :save-ssh-key
                 api-ch
                 :params ssh-key
                 :context {:project-id project-id})))


(defmethod post-control-event! :deleted-ssh-key
  [target message {:keys [project-id hostname fingerprint]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (ajax/ajax :delete
               (api-path/project-ssh-key vcs-type project-name)
               :delete-ssh-key
               api-ch
               :params {:fingerprint fingerprint
                        :hostname (str hostname)} ; coerce nil to ""
               :context {:project-id project-id
                         :hostname hostname
                         :fingerprint fingerprint})))


(defmethod post-control-event! :test-hook
  [target message {:keys [project-id merge-paths service]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        comms (get-in current-state [:comms])]
    (go
      (let [save-result (<! (save-project-settings project-id merge-paths current-state))
            test-result (if (= (:status save-result) :success)
                          (let [test-result
                                (<! (ajax/managed-ajax
                                      :post (api-path/project-hook-test vcs-type project-name service)
                                      :params {:project-id project-id}))]
                            (when (not= (:status test-result) :success)
                              (put! (:errors comms) [:api-error test-result]))
                            test-result)
                          save-result)]
        (release-button! uuid (:status test-result))))))


(defmethod post-control-event! :saved-project-api-token
  [target message {:keys [project-id api-token]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (api-path/project-tokens vcs-type project-name)
                 :save-project-api-token
                 api-ch
                 :params api-token
                 :context {:project-id project-id})))


(defmethod post-control-event! :deleted-project-api-token
  [target message {:keys [project-id token]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (ajax/ajax :delete
               (api-path/project-token vcs-type project-name token)
               :delete-project-api-token
               api-ch
               :context {:project-id project-id
                         :token token})))


(defmethod post-control-event! :set-heroku-deploy-user
  [target message {:keys [project-id login]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (api-path/heroku-deploy-user vcs-type project-name)
                 :set-heroku-deploy-user
                 api-ch
                 :context {:project-id project-id
                           :login login})))


(defmethod post-control-event! :removed-heroku-deploy-user
  [target message {:keys [project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :delete
                 (api-path/heroku-deploy-user vcs-type project-name)
                 :remove-heroku-deploy-user
                 api-ch
                 :context {:project-id project-id})))


(defmethod post-control-event! :set-user-session-setting
  [target message {:keys [setting value]} previous-state current-state]
  (set! (.. js/window -location -search) (str "?" (name setting) "=" value)))


(defmethod post-control-event! :load-first-green-build-github-users
  [target message {:keys [vcs_type project-name]} previous-state current-state]
  (if (or (nil? vcs_type) (= "github" vcs_type))
    (ajax/ajax :get
               (api-path/project-users vcs_type project-name)
               :first-green-build-github-users
               (get-in current-state [:comms :api])
               :context {:project-name project-name})))

(defmethod post-control-event! :invited-github-users
  [target message {:keys [vcs_type project-name org-name invitees]} previous-state current-state]
  (let [project-vcs-type (or vcs_type "github")
        org-vcs-type (or vcs_type "github")
        context (if project-name
                  ;; TODO: non-hackish way to indicate the type of invite
                  {:project project-name :first_green_build true}
                  {:org org-name})]
    (button-ajax :post
                 (if project-name
                   (api-path/project-users-invite project-vcs-type project-name)
                   (api-path/organization-invite org-vcs-type org-name))
                 :invite-github-users
                 (get-in current-state [:comms :api])
                 :context context
                 :params invitees
                 :events {:success #(analytics/track {:event-type :teammates-invited
                                                      :current-state current-state
                                                      :properties {:vcs-type vcs_type
                                                                   :invitees invitees
                                                                   :invitee-count (count invitees)}})})))

(defmethod post-control-event! :report-build-clicked
  [target message {:keys [build-url]} previous-state current-state]
  (support/raise-dialog (get-in current-state [:comms :errors])))

(defmethod post-control-event! :cancel-build-clicked
  [target message {:keys [vcs-url build-num build-id]} previous-state current-state]
  (let [api-ch (-> current-state :comms :api)
        vcs-type (vcs-url/vcs-type vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (api-path/build-cancel vcs-type org-name repo-name build-num)
                 :cancel-build
                 api-ch
                 :context {:build-id build-id})))

(defmethod post-control-event! :enabled-project
  [target message {:keys [vcs-url project-name project-id]} previous-state current-state]
  (button-ajax :post
               (api-path/project-enable (vcs-url/vcs-type vcs-url) project-name)
               :enable-project
               (get-in current-state [:comms :api])
               :context {:project-name project-name
                         :project-id project-id}))

(defmethod post-control-event! :new-plan-clicked
  [target message {:keys [containers price description linux]} previous-state current-state]
  (utils/mlog "handling new-plan-clicked")
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (utils/mlog "calling stripe/open-checkout")
    (stripe/open-checkout {:price price :description description} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (condp = message
            :stripe-checkout-closed (release-button! uuid :idle)
            :stripe-checkout-succeeded
            (let [card-info (:card data)]
              (put! api-ch [:plan-card :success {:resp card-info
                                                 :context {:org-name org-name}}])
              (let [api-result (<! (ajax/managed-ajax
                                    :post
                                    (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                    vcs-type
                                                    org-name
                                                    "plan")
                                    :params {:token data
                                             :containers containers
                                             :billing-name org-name
                                             :billing-email (get-in current-state (conj state/user-path :selected_email))
                                             :paid linux}))]
                (put! api-ch [:create-plan
                              (:status api-result)
                              (assoc api-result :context {:org-name org-name
                                                          :vcs-type vcs-type})])
                (release-button! uuid (:status api-result))))
            nil)))))

(defmethod post-control-event! :new-osx-plan-clicked
  [target message {:keys [plan-type price description]} previous-state current-state]
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]

    (utils/mlog "calling stripe/open-checkout")
    (stripe/open-checkout {:price price :description description} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (condp = message
            :stripe-checkout-closed (release-button! uuid :idle)
            :stripe-checkout-succeeded
            (let [card-info (:card data)]
              (put! api-ch [:plan-card :success {:resp card-info
                                                 :context {:org-name org-name}}])
              (let [api-result (<! (ajax/managed-ajax
                                     :post
                                     (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                     vcs-type
                                                     org-name)
                                     :params {:token data
                                              :billing-name org-name
                                              :billing-email (get-in current-state (conj state/user-path :selected_email))
                                              :osx plan-type}))]
                (put! api-ch [:create-plan
                              (:status api-result)
                              (assoc api-result :context {:org-name org-name
                                                          :vcs-type vcs-type})])
                (release-button! uuid (:status api-result))))
            nil)))))

(defmethod post-control-event! :new-checkout-key-clicked
  [target message {:keys [project-id project-name key-type]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        err-ch (get-in current-state [:comms :errors])
        vcs-type (vcs-url/vcs-type project-id)]
    (go (let [api-resp (<! (ajax/managed-ajax
                             :post
                             (api-path/project-checkout-keys vcs-type project-name)
                             :params {:type key-type}))]
          (if (= :success (:status api-resp))
            (let [api-resp (<! (ajax/managed-ajax :get (api-path/project-checkout-keys vcs-type project-name)))]
              (put! api-ch [:project-checkout-key (:status api-resp) (assoc api-resp :context {:project-name project-name})]))
            (put! err-ch [:api-error api-resp]))
          (release-button! uuid (:status api-resp))))))

(defmethod post-control-event! :delete-checkout-key-clicked
  [target message {:keys [project-id project-name fingerprint]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        err-ch (get-in current-state [:comms :errors])
        vcs-type (vcs-url/vcs-type project-id)]
    (go (let [api-resp (<! (ajax/managed-ajax
                             :delete
                             (api-path/project-checkout-key vcs-type project-name fingerprint)))]
          (if (= :success (:status api-resp))
            (let [api-resp (<! (ajax/managed-ajax :get (api-path/project-checkout-keys vcs-type project-name)))]
              (put! api-ch [:project-checkout-key (:status api-resp) (assoc api-resp :context {:project-name project-name})]))
            (put! err-ch [:api-error api-resp]))
          (release-button! uuid (:status api-resp))))))

(defmethod post-control-event! :update-containers-clicked
  [target message {:keys [containers]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)
        login (get-in current-state state/user-login-path)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                           vcs-type
                                           org-name)
                           :params {:containers containers}))]
       (put! api-ch [:update-plan
                     (:status api-result)
                     (assoc api-result :context {:org-name org-name
                                                 :vcs-type vcs-type})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :update-osx-plan-clicked
  [target message {:keys [plan-type]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :put
                             (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                             vcs-type
                                             org-name)
                             :params {:osx plan-type}))]
        (put! api-ch [:update-plan
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :activate-plan-trial
  [target message {:keys [plan-type template org]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        {org-name :name vcs-type :vcs_type} org
        api-ch (get-in current-state [:comms :api])
        nav-ch (get-in current-state [:comms :nav])]
    (analytics/track {:event-type :start-trial-clicked
                      :current-state current-state
                      :properties {:org org-name
                                   :vcs-type vcs-type
                                   :plan-type (analytics-utils/canonical-plan-type plan-type)
                                   :template template}})
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :post
                             (gstring/format "/api/v1.1/organization/%s/%s/plan/trial"
                                             vcs-type
                                             org-name)
                             :params {plan-type {:template template}}))]
        (put! api-ch [:update-plan
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (if (and (= :build (get-in current-state state/current-view-path))
                 (= :success (:status api-result)))
         (put! nav-ch [:navigate! {:path (routes/v1-project-settings-path {:vcs_type "github"
                                                                           :org org-name
                                                                           :repo (get-in current-state state/project-repo-path)
                                                                           :_fragment "parallel-builds"})}]))
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :save-piggieback-orgs-clicked
  [target message {:keys [selected-piggieback-orgs org-name vcs-type]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        piggieback-org-maps (map #(set/rename-keys % {:vcs_type :vcs-type})
                                 selected-piggieback-orgs)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                           vcs-type
                                           org-name)
                           :params {:piggieback-org-maps piggieback-org-maps}))]
       (put! api-ch [:update-plan
                     (:status api-result)
                     (assoc api-result :context {:org-name org-name
                                                 :vcs-type vcs-type})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :transfer-plan-clicked
  [target message {{:keys [org-name vcs-type]} :from-org :keys [to-org]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        errors-ch (get-in current-state [:comms :errors])
        nav-ch (get-in current-state [:comms :nav])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1.1/organization/%s/%s/transfer-plan"
                                           vcs-type
                                           org-name)
                           :params to-org))]
       (if-not (= :success (:status api-result))
         (put! errors-ch [:api-error api-result])
         (let [plan-api-result (<! (ajax/managed-ajax :get (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                                           vcs-type
                                                                           org-name)))]
           (put! api-ch [:org-plan
                         (:status plan-api-result)
                         (assoc plan-api-result
                                :context
                                {:org-name org-name
                                 :vcs-type vcs-type})])
           (put! nav-ch [:navigate! {:path (routes/v1-org-settings-path {:org org-name
                                                                         :vcs_type vcs-type})}])))
       (release-button! uuid (:status api-result))))))

(defn- maybe-add-message-for-beta
  "Adds a message if a user changes their beta status.  Must be run
  before actually updating state."
  [state args]

  (if-not (contains? args state/user-in-beta-key)
    state
    (let [before (boolean (get-in state state/user-in-beta-path))
          after (boolean (args state/user-in-beta-key))]
      (case [before after]
        [true false] (assoc-in state state/general-message-path "You have left the beta program! Come back any time!")
        [false true] (assoc-in state state/general-message-path "You have joined the beta program! Thanks!")
        state))))

(defmethod control-event :preferences-updated
  [target message args state]
  (-> state
      (maybe-add-message-for-beta args)
      (update-in state/user-path merge args)))

(defmethod post-control-event! :preferences-updated
  [target message args previous-state current-state]
  (let [beta-params {state/user-in-beta-key         (get-in current-state state/user-in-beta-path)
                     state/user-betas-key           (get-in current-state state/user-betas-path)}
        email-params {state/user-email-prefs-key    (get-in current-state state/user-email-prefs-path)
                      state/user-selected-email-key (get-in current-state state/user-selected-email-path)}
        api-ch (get-in current-state [:comms :api])]
    (ajax/ajax
     :put
     "/api/v1/user/save-preferences"
     :update-preferences
     api-ch
     :params (merge beta-params email-params))
    (launchdarkly/merge-custom-properties! beta-params)))

(defmethod control-event :project-preferences-updated
  [target message args state]
  (update-in state (conj state/user-path :projects)
             (partial merge-with merge)
             ;; The keys of the projects map are unfortunately keywords, despite being URLs.
             (into {} (for [[vcs-url prefs] args]
                        [(keyword vcs-url) prefs]))))

(defmethod post-control-event! :project-preferences-updated
  [target message args previous-state current-state]
  (ajax/ajax
   :put
   "/api/v1/user/save-preferences"
   :update-preferences
   (get-in current-state [:comms :api])
   :params {:projects args}))

(defmethod control-event :org-preferences-updated
  [target message {:keys [org prefs]} state]
  ;; org is expected to be a vector [vcs_type username] where both are
  ;; keywords
  (update-in state
             (into state/user-org-prefs-path org)
             merge
             prefs))

(defmethod post-control-event! :org-preferences-updated
  [target message {:keys [org prefs]} previous-state current-state]
  (ajax/ajax
   :put
   "/api/v1/user/save-preferences"
   :update-preferences
   (get-in current-state [:comms :api])
   :params {:organization_prefs (assoc-in {} org prefs)}))

(defmethod post-control-event! :heroku-key-add-attempted
  [target message args previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :post
                           "/api/v1/user/heroku-key"
                           :params {:apikey (:heroku_api_key args)}))]
       (if (= :success (:status api-result))
         (let [me-result (<! (ajax/managed-ajax :get "/api/v1/me"))]
           (put! api-ch [:update-heroku-key :success api-result])
           (put! api-ch [:me (:status me-result) (assoc me-result :context {})]))
         (put! (get-in current-state [:comms :errors]) [:api-error api-result]))
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :api-token-revocation-attempted
  [target message {:keys [token]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :delete
                           (gstring/format "/api/v1/user/token/%s" (:token token))
                           :params {}))]
       (put! api-ch [:delete-api-token (:status api-result) (assoc api-result :context {:token token})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :api-token-creation-attempted
  [target message {:keys [label]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :post
                           "/api/v1/user/token"
                           :params {:label label}))]
       (put! api-ch [:create-api-token (:status api-result) (assoc api-result :context {:label label})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :update-card-clicked
  [target message {:keys [containers price description base-template-id]} previous-state current-state]
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (stripe/open-checkout {:panelLabel "Update card"} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (condp = message
            :stripe-checkout-closed (release-button! uuid :idle)
            :stripe-checkout-succeeded
            (let [token-id (:id data)]
              (let [api-result (<! (ajax/managed-ajax
                                    :put
                                    (gstring/format "/api/v1.1/organization/%s/%s/card"
                                                    vcs-type
                                                    org-name)
                                    :params {:token token-id}))]
                (put! api-ch [:plan-card (:status api-result) (assoc api-result :context {:vcs-type vcs-type
                                                                                          :org-name org-name})])
                (release-button! uuid (:status api-result))))
            nil)))))

(defmethod post-control-event! :save-invoice-data-clicked
  [target message _ previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)
        settings (state-utils/merge-inputs (get-in current-state state/org-plan-path)
                                           (get-in current-state state/inputs-path)
                                           [:billing_email :billing_name :extra_billing_data])]
    (go
      (let [api-result (<! (ajax/managed-ajax
                              :put
                              (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                              vcs-type
                                              org-name)
                              :params {:billing-email (:billing_email settings)
                                       :billing-name (:billing_name settings)
                                       :extra-billing-data (:extra_billing_data settings)}))]
        (when (= :success (:status api-result))
          (put! (get-in current-state [:comms :controls]) [:clear-inputs (map vector (keys settings))]))
        (put! api-ch [:update-plan
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :resend-invoice-clicked
  [target message {:keys [invoice-id]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                              :post
                              (gstring/format "/api/v1.1/organization/%s/%s/invoice/resend"
                                              vcs-type
                                              org-name)
                              :params {:id invoice-id}))]
        ;; TODO Handle this message in the API channel
        (put! api-ch [:resend-invoice
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :cancel-plan-clicked
  [target message {:keys [org-name vcs_type cancel-reasons cancel-notes]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        nav-ch (get-in current-state [:comms :nav])
        errors-ch (get-in current-state [:comms :errors])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :delete
                           (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                           vcs_type
                                           org-name)
                           :params {:cancel-reasons cancel-reasons :cancel-notes cancel-notes}))]
       (if-not (= :success (:status api-result))
         (put! errors-ch [:api-error api-result])
         (let [plan-api-result (<! (ajax/managed-ajax
                                    :get
                                    (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                    vcs_type
                                                    org-name)))]
           (put! api-ch [:org-plan (:status plan-api-result) (assoc plan-api-result :context {:org-name org-name})])
           (put! nav-ch [:navigate! {:path (routes/v1-org-settings {:org org-name
                                                                    :vcs_type vcs_type})
                                     :replace-token? true}])))
       (release-button! uuid (:status api-result))))))

(defn track-and-redirect [event properties owner path]
  (let [redirect #(set! js/window.location.href path)
        track-ch (analytics/track {:event-type :external-click
                                   :event event
                                   :owner owner
                                   :properties properties})]
    (if track-ch
      (go (alt!
            track-ch ([v] (do (utils/mlog "tracked" v "... redirecting")
                              (redirect)))
            (async/timeout 1000) (do (utils/mlog "timing out waiting for analytics. redirecting.")
                                     (redirect))))
      (redirect))))

(defmethod post-control-event! :track-external-link-clicked
  [_ _ {:keys [event properties owner path]} _ _]
  (track-and-redirect event properties owner path))

(defmethod control-event :project-feature-flag-checked
  [target message {:keys [project-id flag value]} state]
  (assoc-in state (conj state/feature-flags-path flag) value))

(defmethod post-control-event! :project-feature-flag-checked
  [target message {:keys [project-id flag value]} previous-state current-state]
  (analytics-track/project-image-change {:previous-state previous-state
                                         :current-state current-state
                                         :flag flag
                                         :value value})
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])
        error-ch (get-in current-state [:comms :errors])
        org-name (vcs-url/org-name project-id)
        repo-name (vcs-url/repo-name project-id)
        vcs-type (vcs-url/vcs-type project-id)]
    (go (let [api-result (<! (ajax/managed-ajax :put (api-path/project-settings vcs-type org-name repo-name)
                                                :params {:feature_flags {flag value}}))]
          (when (not= :success (:status api-result))
            (put! error-ch [:api-error api-result]))
          (ajax/ajax :get (api-path/project-settings vcs-type org-name repo-name) :project-settings api-ch :context {:project-name project-name})))))

(defmethod post-control-event! :project-experiments-feedback-clicked
  [target message _ previous-state current-state]
  (support/raise-dialog (get-in current-state [:comms :errors])))

(defmethod control-event :refresh-admin-build-state-clicked
  [target message _ state]
  (assoc-in state state/build-state-path nil))

(defmethod post-control-event! :refresh-admin-build-state-clicked
  [target message _ previous-state current-state]
  (api/get-build-state (get-in current-state [:comms :api])))

(defmethod control-event :refresh-admin-fleet-state-clicked
  [target message _ state]
  (assoc-in state state/fleet-state-path nil))

(defmethod post-control-event! :refresh-admin-fleet-state-clicked
  [target message _ previous-state current-state]
  (api/get-fleet-state (get-in current-state [:comms :api])))

(defmethod control-event :clear-error-message-clicked
  [target message _ state]
  (assoc-in state state/error-message-path nil))

(defmethod control-event :refresh-admin-build-list
  [_ _ _ state]
  (assoc state :recent-builds nil))

(defmethod post-control-event! :refresh-admin-build-list
  [target _ {:keys [tab]} _ current-state]
  (api/get-admin-dashboard-builds tab (get-in current-state [:comms :api])))

(defmethod control-event :show-all-commits-toggled
  [target message _ state]
  (update-in state (conj state/build-data-path :show-all-commits?) not))

(defmethod control-event :show-test-message-toggled
  [_ _ {:keys [test-index]} state]
  (let [test-path (concat state/tests-path [test-index :show-message])]
    (update-in state test-path not)))

(defmethod control-event :pricing-parallelism-clicked
  [target message {:keys [p]} state]
  (assoc-in state state/pricing-parallelism-path p))

(defmethod control-event :play-video
  [_ _ video-id state]
  (assoc-in state state/modal-video-id-path video-id))

(defmethod control-event :close-video
  [_ _ _ state]
  (assoc-in state state/modal-video-id-path nil))

(defmethod control-event :change-hamburger-state
  [_ _ _ state]
  (let [hamburger-state (get-in state state/hamburger-menu-path)]
    (if (= "closed" hamburger-state)
      (assoc-in state state/hamburger-menu-path "open")
      (assoc-in state state/hamburger-menu-path "closed"))))

(defmethod post-control-event! :suspend-user
  [_ _ {:keys [login]} _ {{api-ch :api} :comms}]
  (api/set-user-suspension login true api-ch))

(defmethod post-control-event! :unsuspend-user
  [_ _ {:keys [login]} _ {{api-ch :api} :comms}]
  (api/set-user-suspension login false api-ch))

(defmethod post-control-event! :set-admin-scope
  [_ _ {:keys [login scope]} _ {{api-ch :api} :comms}]
  (api/set-user-admin-scope login scope api-ch))

(defmethod control-event :system-setting-changed
  [_ _ {:keys [name]} state]
  (update-in state state/system-settings-path
             (fn [settings]
               (mapv #(if (= name (:name %))
                        (assoc % :updating true)
                        %)
                     settings))))

(defmethod post-control-event! :system-setting-changed
  [_ _ {:keys [name value]} _ {{api-ch :api} :comms}]
  (api/set-system-setting name value api-ch))

(defmethod control-event :insights-sorting-changed
  [_ _ {:keys [new-sorting]} state]
  (assoc-in state state/insights-sorting-path new-sorting))

(defmethod control-event :insights-filter-changed
  [_ _ {:keys [new-filter]} state]
  (assoc-in state state/insights-filter-path new-filter))

(defmethod control-event :dismiss-statuspage
  [_ _ {:keys [last-update]} state]
  (assoc-in state state/statuspage-dismissed-update-path last-update))

(defmethod post-control-event! :upload-p12
  [_ _ {:keys [project-name vcs-type file-content file-name password description on-success]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (api/set-project-code-signing-keys project-name vcs-type file-content file-name password description api-ch uuid on-success)))

(defmethod post-control-event! :delete-p12
  [_ _ {:keys [project-name vcs-type id]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (api/delete-project-code-signing-key project-name vcs-type id api-ch uuid)))

(defmethod post-control-event! :project-insights-branch-changed
  [target message {:keys [new-branch]} _ current-state]
  (let [nav-data (get-in current-state [:navigation-data])
        comms (get-in current-state [:comms])]
    (put! (:nav comms) [:navigate! {:path (routes/v1-insights-project-path (assoc nav-data :branch new-branch))}])
    (analytics/track {:event-type :project-branch-changed
                      :current-state current-state
                      :properties {:new-branch new-branch}})))

(defmethod control-event :logging-enabled-clicked
  [_ _ _ state]
  (update-in state state/logging-enabled-path not))

(defmethod control-event :dismiss-osx-usage-banner
  [_ _ {:keys [current-usage]} current-state]
  (cond
    (>= current-usage plan/third-warning-threshold)
    (assoc-in current-state state/dismissed-osx-usage-level (+ current-usage plan/future-warning-threshold-increment))

    (>= current-usage plan/second-warning-threshold)
    (assoc-in current-state state/dismissed-osx-usage-level plan/third-warning-threshold)

    (>= current-usage plan/first-warning-threshold)
    (assoc-in current-state state/dismissed-osx-usage-level plan/second-warning-threshold)

    :else
    current-state))

(defmethod control-event :dismiss-osx-command-change-banner
  [_ _ _ state]
  (assoc-in state state/dismissed-osx-command-change-banner-path true))

(defmethod control-event :dismiss-trial-offer-banner
  [_ _ _ state]
  (assoc-in state state/dismissed-trial-offer-banner true))

(defmethod post-control-event! :dismiss-trial-offer-banner
  [_ _ {:keys [org plan-type template]} _ current-state]
  (let [{org-name :name vcs-type :vcs_type} org]
    (analytics/track {:event-type :dismiss-trial-offer-banner-clicked
                      :current-state current-state
                      :properties {:org org-name
                                   :vcs-type vcs-type
                                   :plan-type (analytics-utils/canonical-plan-type plan-type)
                                   :template template}})))

(defmethod control-event :dismiss-trial-update-banner
  [_ _ _ state]
  (assoc-in state state/dismissed-trial-update-banner true))

(defmethod control-event :set-web-notifications
  [_ _ {:keys [enabled? response]} state]
  (assoc-in state state/web-notifications-enabled?-path enabled?))

(defmethod post-control-event! :set-web-notifications
  [_ _ {:keys [enabled? response]} state]
  (when enabled?
    (analytics/track {:event-type :set-web-notifications-clicked
                      :current-state state
                      :properties {:response response}})))

(defmethod control-event :asked-about-web-notifications
  [_ _ _ state]
  (assoc-in state state/asked-about-web-notifications? true))

(defmethod control-event :dismiss-web-notif-banner
  [_ _ {:keys [banner-number _]} state]
  (condp = banner-number
    "one"  (assoc-in state state/dismissed-web-notif-banner-one? true)
    "two"  (assoc-in state state/dismissed-web-notif-banner-two? true)))

(defmethod post-control-event! :dismiss-web-notif-banner
  [_ _ {:keys [banner-number response]} current-state]
  (condp = banner-number
    "one" (analytics/track {:event-type :web-notification-banner-one-dismissed
                            :current-state current-state
                            :properties {:response response}})
    "two" (analytics/track {:event-type :web-notification-banner-two-dismissed
                            :current-state current-state})))
