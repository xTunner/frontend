(ns frontend.controllers.controls
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [cljs.reader :as reader]
            [frontend.analytics :as analytics]
            [frontend.api :as api]
            [frontend.async :refer [put!]]
            [frontend.api.path :as api-path]
            [frontend.components.forms :refer [release-button!]]
            [frontend.components.build :as build-component]
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
            [frontend.utils.seq :refer [dissoc-in find-index]]
            [frontend.utils.state :as state-utils]
            [goog.dom]
            [goog.string :as gstring]
            [goog.labs.userAgent.engine :as engine]
            goog.style
            [frontend.models.user :as user-model])
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
        c (chan)]
    (apply ajax/ajax method url message c opts)
    (go-loop []
      (when-let [[_ status _ :as event] (<! c)]
        (when (#{:success :failed} status)
          (release-button! uuid status))
        (>! channel event)
        (recur)))))

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
  [target message {:keys [login]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when (user-model/has-org? (get-in current-state state/user-path) login)
      (api/get-org-settings login api-ch)
      (api/get-org-plan login api-ch)))
  (utils/scroll-to-id! "project-listing"))

(defmethod post-control-event! :refreshed-user-orgs [target message args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (go (let [api-result (<! (ajax/managed-ajax :get "/api/v1/user/organizations"))]
          (put! api-ch [:organizations (:status api-result) api-result])))))

(defmethod post-control-event! :artifacts-showed
  [target message _ previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        build (get-in current-state state/build-path)]
    (ajax/ajax :get
               (gstring/format "/api/v1/project/%s/%s/artifacts"
                               (vcs-url/project-name (:vcs_url build))
                               (:build_num build))
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
                               ;; Store this on the parent so that we don't handle parent scroll while
                               ;; the animation is playing
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
  (when (not= (get-in previous-state state/current-container-path)
              container-id)
    (let [container (get-in current-state (state/container-path container-id))
          last-action (-> container :actions last)]
      (when (and (:has_output last-action)
                 (action-model/visible? last-action)
                 (:missing-pusher-output last-action))
        (api/get-action-output {:vcs-url (:vcs_url (get-in current-state state/build-path))
                                :build-num (:build_num (get-in current-state state/build-path))
                                :step (:step last-action)
                                :index (:index last-action)
                                :output-url (:output_url last-action)}
                               (get-in current-state [:comms :api]))))))

(defmethod control-event :container-paging-offset-changed
  [target message {:keys [paging-offset]} state]
  (assoc-in state state/container-paging-offset-path paging-offset))

(defmethod control-event :container-filter-changed
  [target message {:keys [new-filter containers]} state]
  (let [indexes (map :index containers)
        offset (get-in state state/container-paging-offset-path)
        selected-container (get-in state state/current-container-path)
        selected-index (find-index (partial = selected-container) indexes)
        controls-ch (get-in state [:comms :controls])]
    (if-not (and selected-index
                 (seq containers))
      (put! controls-ch [:container-selected {:container-id (:index (first containers))
                                              :animate? true}]))
    (-> state
        (assoc-in state/current-container-filter-path new-filter)
        (assoc-in state/container-paging-offset-path
                  (if selected-index
                    (* build-component/paging-width
                       (js/Math.floor (/ selected-index build-component/paging-width)))
                    0)))))

(defmethod control-event :action-log-output-toggled
  [target message {:keys [index step value]} state]
  (assoc-in state (state/show-action-output-path index step) value))

(defmethod post-control-event! :action-log-output-toggled
  [target message {:keys [index step] :as args} previous-state current-state]
  (let [action (get-in current-state (state/action-path index step))
        build (get-in current-state state/build-path)]
    (when (and (action-model/visible? action)
               (:has_output action)
               (not (:output action)))
      (api/get-action-output {:vcs-url (:vcs_url build)
                              :build-num (:build_num build)
                              :step step
                              :index index
                              :output-url (:output_url action)}
                             (get-in current-state [:comms :api])))))


(defmethod control-event :selected-project-parallelism
  [target message {:keys [project-id parallelism]} state]
  (assoc-in state (conj state/project-path :parallel) parallelism))

(defmethod post-control-event! :selected-project-parallelism
  [target message {:keys [project-id parallelism]} previous-state current-state]
  (when (not= (get-in previous-state state/project-path)
              (get-in current-state state/project-path))
    (let [project-name (vcs-url/project-name project-id)
          api-ch (get-in current-state [:comms :api])]
      ;; TODO: edit project settings api call should respond with updated project settings
      (ajax/ajax :put
                 (api-path/settings-path (:navigation-data current-state))
                 :update-project-parallelism
                 api-ch
                 :params {:parallel parallelism}
                 :context {:project-id project-id}))))

(defmethod post-control-event! :clear-cache
  [target message {:keys [type project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])
        uuid frontend.async/*uuid*]
    (ajax/ajax :delete
               (gstring/format "/api/v1/project/%s/%s-cache" project-name type)
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


(defmethod control-event :invite-selected-all
  [target message _ state]
  (update-in state state/invite-github-users-path (fn [users]
                                                    (vec (map #(assoc % :checked true) users)))))


(defmethod control-event :invite-selected-none
  [target message _ state]
  (update-in state state/invite-github-users-path (fn [users]
                                                    (vec (map #(assoc % :checked false) users)))))


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
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        uuid frontend.async/*uuid*]
    (go
     (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1/project/%s/%s/%s/retry" org-name repo-name build-num)
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
        uuid frontend.async/*uuid*]
    (go
     (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1/project/%s/%s/%s/ssh" org-name repo-name build-num)))]
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
        uuid frontend.async/*uuid*]
    (go
      (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1/project/%s/%s/%s/ssh-users" org-name repo-name build-num)))]
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :followed-repo
  [target message repo previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        vcs-url (:vcs_url repo)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/follow" project)
                 :follow-repo
                 api-ch
                 :params {:vcs-type (:vcs_type repo)}
                 :context repo)
    (analytics/track {:event-type :project-followed
                      :current-state current-state
                      :properties {:org org-name
                                   :repo repo-name}})))


(defmethod control-event :inaccessible-org-toggled
  [target message {:keys [org-name value]} state]
  (assoc-in state [:settings :add-projects :inaccessible-orgs org-name :visible?] value))


(defmethod post-control-event! :followed-project
  [target message {:keys [vcs-url project-id]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/follow" project)
                 :follow-project
                 api-ch
                 :context {:project-id project-id})
    (analytics/track {:event-type :project-followed
                      :current-state current-state
                      :properties {:org org-name
                                   :repo repo-name}})))


(defmethod post-control-event! :unfollowed-repo
  [target message repo previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        vcs-url (:vcs_url repo)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/unfollow" project)
                 :unfollow-repo
                 api-ch
                 :params {:vcs-type (:vcs_type repo)}
                 :context repo)
    (analytics/track {:event-type :project-unfollowed
                      :current-state current-state
                      :properties {:org org-name
                                   :repo repo-name}})))


(defmethod post-control-event! :unfollowed-project
  [target message {:keys [vcs-url project-id] :as repo} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/unfollow" project)
                 :unfollow-project
                 api-ch
                 :params {:vcs-type (:vcs_type repo)}
                 :context {:project-id project-id})
    (analytics/track {:event-type :project-unfollowed
                      :current-state current-state
                      :properties {:org org-name
                                   :repo repo-name}})))

(defmethod post-control-event! :stopped-building-project
  [target message {:keys [vcs-url project-id]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        login (get-in current-state state/user-login-path)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :delete
                 (gstring/format "/api/v1/project/%s/enable" project)
                 :stop-building-project
                 api-ch
                 :context {:project-id project-id})
    (analytics/track {:event-type :project-builds-stopped
                      :current-state current-state
                      :properties {:org org-name
                                   :repo repo-name}})))

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
  (let [project-name (vcs-url/project-name project-id)
        uuid frontend.async/*uuid*
        comms (get-in current-state [:comms])
        branch (get-in current-state (conj state/inputs-path :settings-branch) (get-in current-state (conj state/project-path :default_branch)))]
    ;; TODO: edit project settings api call should respond with updated project settings
    (go
     (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1/project/%s/tree/%s" project-name (gstring/urlEncode branch))))]
       (put! (:api comms) [:start-build (:status api-result) api-result])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :created-env-var
  [target message {:keys [project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (case vcs-type
                   "github" (gstring/format "/api/v1/project/%s/envvar" project-name)
                   "bitbucket" (gstring/format "/api/dangerzone/project/%s/%s/envvar" vcs-type project-name))
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
               (case vcs-type
                 "github" (gstring/format "/api/v1/project/%s/envvar/%s" project-name env-var-name)
                 "bitbucket" (gstring/format "/api/dangerzone/project/%s/%s/envvar/%s" vcs-type project-name env-var-name))
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
                             :put (api-path/settings-path (:navigation-data current-state))
                             :params settings))]
       (if (= :success (:status api-result))
         (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/settings-path (:navigation-data current-state))))]
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
        org (vcs-url/org-name project-id)
        repo (vcs-url/repo-name project-id)
        uuid frontend.async/*uuid*
        comms (get-in current-state [:comms])]
    (go
     (let [api-result (<! (ajax/managed-ajax :put (api-path/settings-path (:navigation-data current-state)) :params settings))]
       (if (= :success (:status api-result))
         (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/settings-path (:navigation-data current-state))))]
           (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
           (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}])
           (when start-build?
             (let [build-api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1/project/%s/tree/%s" project-name (gstring/urlEncode branch))))]
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
        settings (merge-settings merge-paths project inputs)]
    (go
      (let [api-result (<! (ajax/managed-ajax :put (api-path/settings-path (:navigation-data current-state)) :params settings))]
        (if (= :success (:status api-result))
          (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/settings-path (:navigation-data current-state))))]
            (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
            (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}]))
          (put! (:errors comms) [:api-error api-result]))
        api-result))))

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
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/ssh-key" project-name)
                 :save-ssh-key
                 api-ch
                 :params ssh-key
                 :context {:project-id project-id})))


(defmethod post-control-event! :deleted-ssh-key
  [target message {:keys [project-id hostname fingerprint]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])]
    (ajax/ajax :delete
               (gstring/format "/api/v1/project/%s/ssh-key" project-name)
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
        comms (get-in current-state [:comms])]
    (go
      (let [save-result (<! (save-project-settings project-id merge-paths current-state))
            test-result (if (= (:status save-result) :success)
                          (let [test-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1/project/%s/hooks/%s/test" project-name service)
                                                                   :params {:project-id project-id}))]
                            (when (not= (:status test-result) :success)
                              (put! (:errors comms) [:api-error test-result]))
                            test-result)
                          save-result)]
        (release-button! uuid (:status test-result))))))


(defmethod post-control-event! :saved-project-api-token
  [target message {:keys [project-id api-token]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/token" project-name)
                 :save-project-api-token
                 api-ch
                 :params api-token
                 :context {:project-id project-id})))


(defmethod post-control-event! :deleted-project-api-token
  [target message {:keys [project-id token]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])]
    (ajax/ajax :delete
               (gstring/format "/api/v1/project/%s/token/%s" project-name token)
               :delete-project-api-token
               api-ch
               :context {:project-id project-id
                         :token token})))


(defmethod post-control-event! :set-heroku-deploy-user
  [target message {:keys [project-id login]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/heroku-deploy-user" project-name)
                 :set-heroku-deploy-user
                 api-ch
                 :context {:project-id project-id
                           :login login})))


(defmethod post-control-event! :removed-heroku-deploy-user
  [target message {:keys [project-id]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])]
    (button-ajax :delete
                 (gstring/format "/api/v1/project/%s/heroku-deploy-user" project-name)
                 :remove-heroku-deploy-user
                 api-ch
                 :context {:project-id project-id})))


(defmethod post-control-event! :set-user-session-setting
  [target message {:keys [setting value]} previous-state current-state]
  (set! (.. js/window -location -search) (str "?" (name setting) "=" value)))


(defmethod post-control-event! :load-first-green-build-github-users
  [target message {:keys [project-name]} previous-state current-state]
  (ajax/ajax :get
             (gstring/format "/api/v1/project/%s/users" project-name)
             :first-green-build-github-users
             (get-in current-state [:comms :api])
             :context {:project-name project-name}))


(defmethod post-control-event! :invited-github-users
  [target message {:keys [project-name org-name invitees]} previous-state current-state]
  (let [context (if project-name
                  ;; TODO: non-hackish way to indicate the type of invite
                  {:project project-name :first_green_build true}
                  {:org org-name})]
    (button-ajax :post
                 (if project-name
                   (gstring/format "/api/v1/project/%s/users/invite" project-name)
                   (gstring/format "/api/v1/organization/%s/invite" org-name))
                 :invite-github-users
                 (get-in current-state [:comms :api])
                 :context context
                 :params invitees)))

(defmethod post-control-event! :report-build-clicked
  [target message {:keys [build-url]} previous-state current-state]
  (support/raise-dialog (get-in current-state [:comms :errors])))

(defmethod post-control-event! :cancel-build-clicked
  [target message {:keys [vcs-url build-num build-id]} previous-state current-state]
  (let [api-ch (-> current-state :comms :api)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (gstring/format "/api/v1/project/%s/%s/%s/cancel" org-name repo-name build-num)
                 :cancel-build
                 api-ch
                 :context {:build-id build-id})))


(defmethod post-control-event! :enabled-project
  [target message {:keys [project-name project-id]} previous-state current-state]
  (button-ajax :post
               (gstring/format "/api/v1/project/%s/enable" project-name)
               :enable-project
               (get-in current-state [:comms :api])
               :context {:project-name project-name
                         :project-id project-id}))

(defmethod post-control-event! :new-plan-clicked
  [target message {:keys [containers price description paid]} previous-state current-state]
  (utils/mlog "handling new-plan-clicked")
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)]
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
                                    (gstring/format "/api/v1/organization/%s/%s" org-name "plan")
                                    :params {:token data
                                             :containers containers
                                             :billing-name org-name
                                             :billing-email (get-in current-state (conj state/user-path :selected_email))
                                             :paid paid}))]
                (put! api-ch [:create-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
                (release-button! uuid (:status api-result))))
            nil)))))

(defmethod post-control-event! :new-osx-plan-clicked
  [target message {:keys [plan-type price description]} previous-state current-state]
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)]

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
                                     (gstring/format "/api/v1/organization/%s/%s" org-name "plan")
                                     :params {:token data
                                              :billing-name org-name
                                              :billing-email (get-in current-state (conj state/user-path :selected_email))
                                              :osx plan-type}))]
                (put! api-ch [:create-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
                (release-button! uuid (:status api-result))))
            nil)))))

(defmethod post-control-event! :new-checkout-key-clicked
  [target message {:keys [project-id project-name key-type]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        err-ch (get-in current-state [:comms :errors])]
    (go (let [api-resp (<! (ajax/managed-ajax
                             :post
                             (gstring/format "/api/v1/project/%s/checkout-key" project-name)
                             :params {:type key-type}))]
          (if (= :success (:status api-resp))
            (let [api-resp (<! (ajax/managed-ajax :get (gstring/format "/api/v1/project/%s/checkout-key" project-name)))]
              (put! api-ch [:project-checkout-key (:status api-resp) (assoc api-resp :context {:project-name project-name})]))
            (put! err-ch [:api-error api-resp]))
          (release-button! uuid (:status api-resp))))))

(defmethod post-control-event! :delete-checkout-key-clicked
  [target message {:keys [project-id project-name fingerprint]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        err-ch (get-in current-state [:comms :errors])]
    (go (let [api-resp (<! (ajax/managed-ajax
                             :delete
                             (gstring/format "/api/v1/project/%s/checkout-key/%s" project-name fingerprint)))]
          (if (= :success (:status api-resp))
            (let [api-resp (<! (ajax/managed-ajax :get (gstring/format "/api/v1/project/%s/checkout-key" project-name)))]
              (put! api-ch [:project-checkout-key (:status api-resp) (assoc api-resp :context {:project-name project-name})]))
            (put! err-ch [:api-error api-resp]))
          (release-button! uuid (:status api-resp))))))

(defmethod post-control-event! :update-containers-clicked
  [target message {:keys [containers]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)
        login (get-in current-state state/user-login-path)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1/organization/%s/%s" org-name "plan")
                           :params {:containers containers}))]
       (put! api-ch [:update-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
       (release-button! uuid (:status api-result))))
    (let [previous-num-containers (get-in previous-state (conj state/org-plan-path :containers))
          new-num-containers containers
          upgrade? (> new-num-containers previous-num-containers)]
      (analytics/track {:event-type :container-amount-changed
                        :current-state current-state
                        :properties {:previous-num-containers previous-num-containers
                                     :new-num-containers new-num-containers
                                     :is-upgrade upgrade?}}))))

(defmethod post-control-event! :update-osx-plan-clicked
  [target message {:keys [plan-type]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :put
                             (gstring/format "/api/v1/organization/%s/%s" org-name "plan")
                             :params {:osx plan-type}))]
        (put! api-ch [:update-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :activate-plan-trial
  [target message plan-template previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :post
                             (gstring/format "/api/v1/organization/%s/plan/trial" org-name)
                             :params plan-template))]
        (put! api-ch [:update-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :save-piggyback-orgs-clicked
  [target message {:keys [selected-piggyback-orgs org-name]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1/organization/%s/%s" org-name "plan")
                           :params {:piggieback-orgs selected-piggyback-orgs}))]
       (put! api-ch [:update-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :transfer-plan-clicked
  [target message {:keys [to org-name vcs_type]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        errors-ch (get-in current-state [:comms :errors])
        nav-ch (get-in current-state [:comms :nav])]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1/organization/%s/%s" org-name "transfer-plan")
                           :params {:org-name to}))]
       (if-not (= :success (:status api-result))
         (put! errors-ch [:api-error api-result])
         (let [plan-api-result (<! (ajax/managed-ajax :get (gstring/format "/api/v1/organization/%s/plan" org-name)))]
           (put! api-ch [:org-plan (:status plan-api-result) (assoc plan-api-result :context {:org-name org-name})])
           (put! nav-ch [:navigate! {:path (routes/v1-org-settings-path {:org org-name
                                                                         :vcs_type vcs_type})}])))
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
        org-name (get-in current-state state/org-name-path)]
    (stripe/open-checkout {:panelLabel "Update card"} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (condp = message
            :stripe-checkout-closed (release-button! uuid :idle)
            :stripe-checkout-succeeded
            (let [token-id (:id data)]
              (let [api-result (<! (ajax/managed-ajax
                                    :put
                                    (gstring/format "/api/v1/organization/%s/card" org-name)
                                    :params {:token token-id}))]
                (put! api-ch [:plan-card (:status api-result) (assoc api-result :context {:org-name org-name})])
                (release-button! uuid (:status api-result))))
            nil)))))

(defmethod post-control-event! :save-invoice-data-clicked
  [target message _ previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)
        settings (state-utils/merge-inputs (get-in current-state state/org-plan-path)
                                           (get-in current-state state/inputs-path)
                                           [:billing_email :billing_name :extra_billing_data])]
    (go
      (let [api-result (<! (ajax/managed-ajax
                              :put
                              (gstring/format "/api/v1/organization/%s/plan" org-name)
                              :params {:billing-email (:billing_email settings)
                                       :billing-name (:billing_name settings)
                                       :extra-billing-data (:extra_billing_data settings)}))]
        (when (= :success (:status api-result))
          (put! (get-in current-state [:comms :controls]) [:clear-inputs (map vector (keys settings))]))
        (put! api-ch [:update-plan (:status api-result) (assoc api-result :context {:org-name org-name})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :resend-invoice-clicked
  [target message {:keys [invoice-id]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])
        org-name (get-in current-state state/org-name-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                              :post
                              (gstring/format "/api/v1/organization/%s/invoice/resend" org-name)
                              :params {:id invoice-id}))]
        ;; TODO Handle this message in the API channel
        (put! api-ch [:resend-invoice (:status api-result) (assoc api-result :context {:org-name org-name})])
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
                           (gstring/format "/api/v1/organization/%s/plan" org-name)
                           :params {:cancel-reasons cancel-reasons :cancel-notes cancel-notes}))]
       (if-not (= :success (:status api-result))
         (put! errors-ch [:api-error api-result])
         (let [plan-api-result (<! (ajax/managed-ajax :get (gstring/format "/api/v1/organization/%s/plan" org-name)))]
           (put! api-ch [:org-plan (:status plan-api-result) (assoc plan-api-result :context {:org-name org-name})])
           (put! nav-ch [:navigate! {:path (routes/v1-org-settings {:org org-name
                                                                    :vcs_type vcs_type})
                                     :replace-token? true}])
           (analytics/track {:event-type :plan-cancelled
                             :current-state current-state})))
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

(defmethod control-event :language-testimonial-tab-selected
  [target message {:keys [index]} state]
  (assoc-in state state/language-testimonial-tab-path index))

(defmethod control-event :project-feature-flag-checked
  [target message {:keys [project-id flag value]} state]
  (assoc-in state (conj state/project-path :feature_flags flag) value))

(defmethod post-control-event! :project-feature-flag-checked
  [target message {:keys [project-id flag value]} previous-state current-state]
  (let [project-name (vcs-url/project-name project-id)
        api-ch (get-in current-state [:comms :api])
        error-ch (get-in current-state [:comms :errors])]
    (go (let [api-result (<! (ajax/managed-ajax :put (api-path/settings-path (:navigation-data current-state))
                                                :params {:feature_flags {flag value}}))]
          (when (not= :success (:status api-result))
            (put! error-ch [:api-error api-result]))
          (ajax/ajax :get (api-path/settings-path (:navigation-data current-state)) :project-settings api-ch :context {:project-name project-name})))))

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

(defmethod post-control-event! :doc-search-submitted
  [target message {:keys [query]} previous-state current-state]
  (let [comms (get-in current-state [:comms])]
    (go (let [api-result (<! (ajax/managed-ajax :get "/search-articles" :params {:query query}))]
          (put! (:api comms) [:docs-articles (:status api-result) api-result])
          (when (= :success (:status api-result))
            (put! (:nav comms) [:navigate! {:path "/docs/search"}]))))))

(defn scroll-home-by-offset!
  "Scrolls down to the next section of copy on the landing page."
  [target index]
  (let [body (.-body js/document)
        vh (.-height (goog.dom/getViewportSize))]
    (.play (goog.fx.dom.Scroll. body
                                #js [(.-scrollLeft body) (.-scrollTop body)]
                                #js [(.-scrollLeft body) (* index vh)]
                                250))))

(defmethod post-control-event! :home-scroll-1st-clicked
  [target _ _ _ _]
  (scroll-home-by-offset! target 1))

(defmethod post-control-event! :home-scroll-2nd-clicked
  [target _ _ _ _]
  (scroll-home-by-offset! target 2))

(defmethod post-control-event! :home-scroll-3rd-clicked
  [target _ _ _ _]
  (scroll-home-by-offset! target 3))

(defmethod post-control-event! :home-scroll-4th-clicked
  [target _ _ _ _]
  (scroll-home-by-offset! target 4))

(defmethod post-control-event! :home-scroll-5th-clicked
  [target _ _ _ _]
  (scroll-home-by-offset! target 5))

(defmethod post-control-event! :home-scroll-logo-clicked
  [target message _ previous-state current-state]
  (let [body (.-body js/document)
        vh (.-height (goog.dom/getViewportSize))]
    (.play (goog.fx.dom.Scroll. body
                         #js [(.-scrollLeft body) (.-scrollTop body)]
                         #js [(.-scrollLeft body) 0]
                         0))))

(defmethod control-event :customer-logo-clicked
  [target message {:keys [customer]} state]
  (assoc-in state state/customer-logo-customer-path customer))

(defmethod control-event :toolset-clicked
  [target message {:keys [toolset]} state]
  (assoc-in state state/selected-toolset-path toolset))

(defmethod control-event :pricing-parallelism-clicked
  [target message {:keys [p]} state]
  (assoc-in state state/pricing-parallelism-path p))

(defmethod control-event :play-video
  [_ _ video-id state]
  (assoc-in state state/modal-video-id-path video-id))

(defmethod control-event :close-video
  [_ _ _ state]
  (assoc-in state state/modal-video-id-path nil))

(defmethod control-event :top-nav-changed
  [_ _ {:keys [org]} state]
  (assoc-in state state/top-nav-selected-org-path org))

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
  [_ _ {:keys [project-name file-content file-name password description on-success]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (api/set-project-code-signing-keys project-name file-content file-name password description api-ch uuid on-success)))

(defmethod post-control-event! :delete-p12
  [_ _ {:keys [project-name id]} previous-state current-state]
  (let [uuid frontend.async/*uuid*
        api-ch (get-in current-state [:comms :api])]
    (api/delete-project-code-signing-key project-name id api-ch uuid)))

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
