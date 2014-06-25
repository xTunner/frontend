(ns frontend.controllers.navigation
  (:require [frontend.async :refer [put!]]
            [frontend.api :as api]
            [frontend.pusher :as pusher]
            [frontend.state :as state]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :refer [mlog merror]]
            [goog.string :as gstring])
  (:require-macros [frontend.utils :refer [inspect]]))

;; XXX we could really use some middleware here, so that we don't forget to
;;     assoc things in state on every handler
;;     We could also use a declarative way to specify each page.

;; --- Helper Methods ---

(defn set-page-title! [& [title]]
  (set! (.-title js/document) (if title
                                (str title  " - CircleCI")
                                "CircleCI")))

;; --- Navigation Multimethod Declarations ---

(defmulti navigated-to
  (fn [history-imp navigation-point args state] navigation-point))

(defmulti post-navigated-to!
  (fn [history-imp navigation-point args previous-state current-state]
    (put! (get-in current-state [:comms :ws]) [:unsubscribe-stale-channels])
    navigation-point))

;; --- Navigation Multimethod Implementations ---

(defmethod navigated-to :default
  [history-imp navigation-point args state]
  (mlog "Unknown nav event: " (pr-str navigation-point))
  state)

(defmethod post-navigated-to! :default
  [history-imp navigation-point args previous-state current-state]
  (mlog "No post-nav for: " navigation-point))

(defmethod post-navigated-to! :navigate!
  [history-imp navigation-point path previous-state current-state]
  (let [path (if (= \/ (first path))
               (subs path 1)
               path)]
    (.setToken history-imp path)))


(defmethod navigated-to :dashboard
  [history-imp navigation-point args state]
  (-> state
      (assoc :navigation-point navigation-point
             :navigation-data args
             :navigation-settings {:show-settings-link (boolean (:org args))})
      (state-utils/set-dashboard-crumbs args)
      state-utils/reset-current-build))

(defmethod post-navigated-to! :dashboard
  [history-imp navigation-point args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (api/get-dashboard-builds (:navigation-data current-state) api-ch)
    (when (:repo args)
      (ajax/ajax :get
                 (gstring/format "/api/v1/project/%s/%s/settings" (:org args) (:repo args))
                 :project-settings
                 api-ch
                 :context {:project-name (str (:org args) "/" (:repo args))})))
  (set-page-title!))


(defmethod navigated-to :build
  [history-imp navigation-point {:keys [project-name build-num org repo] :as args} state]
  (-> state
      (assoc :navigation-point navigation-point
             :navigation-data args
             :navigation-settings {:show-settings-link true}
             :project-settings-project-name project-name)
      (assoc-in state/crumbs-path [{:type :org :username org}
                                   {:type :project :username org :project repo}
                                   {:type :project-branch :username org :project repo}
                                   {:type :build :username org :project repo
                                    :build-num build-num}])
      state-utils/reset-current-build
      (#(if (state-utils/stale-current-project? % project-name)
          (state-utils/reset-current-project %)
          %))))

;; XXX: add unsubscribe when you leave the build page
(defmethod post-navigated-to! :build
  [history-imp navigation-point {:keys [project-name build-num]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        ws-ch (get-in current-state [:comms :ws])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (ajax/ajax :get
               (gstring/format "/api/v1/project/%s/%s" project-name build-num)
               :build
               api-ch
               :context {:project-name project-name :build-num build-num})
    (when (not (get-in current-state state/project-path))
      (ajax/ajax :get
                 (gstring/format "/api/v1/project/%s/settings" project-name)
                 :project-settings
                 api-ch
                 :context {:project-name project-name}))
    (when (not (get-in current-state state/project-plan-path))
      (ajax/ajax :get
                 (gstring/format "/api/v1/project/%s/plan" project-name)
                 :project-plan
                 api-ch
                 :context {:project-name project-name}))
    (put! ws-ch [:subscribe {:channel-name (pusher/build-channel-from-parts {:project-name project-name
                                                                             :build-num build-num})
                             :messages pusher/build-messages}]))
  (set-page-title! (str project-name " #" build-num)))


(defmethod navigated-to :add-projects
  [history-imp navigation-point _ state]
  (assoc state :navigation-point navigation-point :navigation-data {} :navigation-settings {}))

(defmethod post-navigated-to! :add-projects
  [history-imp navigation-point _ previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (ajax/ajax :get "/api/v1/user/organizations" :organizations api-ch)
    (ajax/ajax :get "/api/v1/user/collaborator-accounts" :collaborators api-ch))
  (set-page-title! "Add projects"))


(defmethod navigated-to :project-settings
  [history-imp navigation-point {:keys [project-name subpage org repo] :as args} state]
  (-> state
      (assoc :navigation-point navigation-point
             :navigation-data args
             :navigation-settings {}
             ;; XXX can we get rid of project-settings-subpage in favor of navigation-data?
             :project-settings-subpage subpage
             :project-settings-project-name project-name)
      (assoc-in state/crumbs-path [{:type :org
                                    :username org}
                                   {:type :project
                                    :username org
                                    :project repo}
                                   {:type :project-settings
                                    :username org
                                    :project repo}])
      (#(if (state-utils/stale-current-project? % project-name)
          (state-utils/reset-current-project %)
          %))))

;; XXX: find a better place for all of the ajax functions, maybe a separate api
;;      namespace that knows about all of the api routes?
(defmethod post-navigated-to! :project-settings
  [history-imp navigation-point {:keys [project-name subpage]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (if (get-in current-state state/project-path)
      (mlog "project settings already loaded for" project-name)
      (ajax/ajax :get
                 (gstring/format "/api/v1/project/%s/settings" project-name)
                 :project-settings
                 api-ch
                 :context {:project-name project-name}))

    (cond (and (= subpage :parallel-builds)
               (not (get-in current-state state/project-plan-path)))
          (ajax/ajax :get
                     (gstring/format "/api/v1/project/%s/plan" project-name)
                     :project-plan
                     api-ch
                     :context {:project-name project-name})

          (and (= subpage :api)
               (not (get-in current-state state/project-tokens-path)))
          (ajax/ajax :get
                     (gstring/format "/api/v1/project/%s/token" project-name)
                     :project-token
                     api-ch
                     :context {:project-name project-name})

          (and (= subpage :env-vars)
               (not (get-in current-state state/project-envvars-path)))
          (ajax/ajax :get
                     (gstring/format "/api/v1/project/%s/envvar" project-name)
                     :project-envvar
                     api-ch
                     :context {:project-name project-name})
          :else nil))

  ;; XXX: check for XSS
  (set-page-title! (str "Edit settings - " project-name)))


(defmethod navigated-to :org-settings
  [history-imp navigation-point {:keys [subpage org-name] :as args} state]
  (mlog "Navigated to subpage:" subpage)
  (-> state
      (assoc :navigation-point navigation-point)
      (assoc :nagivation-data args)
      (assoc :org-settings-subpage subpage)
      (assoc :org-settings-org-name org-name)
      (#(if (state-utils/stale-current-org? % org-name)
          (state-utils/reset-current-org %)
          %))))

(defmethod post-navigated-to! :org-settings
  [history-imp navigation-point {:keys [org-name subpage]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (if (get-in current-state state/org-plan-path)
      (mlog "plan details already loaded for" org-name)
      (ajax/ajax :get
                 (gstring/format "/api/v1/organization/%s/plan" org-name)
                 :org-plan
                 api-ch
                 :context {:org-name org-name}))
    (if (= org-name (get-in current-state state/org-name-path))
      (mlog "organization details already loaded for" org-name)
      (ajax/ajax :get
                 (gstring/format "/api/v1/organization/%s/settings" org-name)
                 :org-settings
                 api-ch
                 :context {:org-name org-name}))
    (condp = subpage
      :organizations (ajax/ajax :get "/api/v1/user/organizations" :organizations api-ch)
      :billing (do
                 (ajax/ajax :get
                            (gstring/format "/api/v1/organization/%s/card" org-name)
                            :plan-card
                            api-ch
                            :context {:org-name org-name})
                 (ajax/ajax :get
                            (gstring/format "/api/v1/organization/%s/invoices" org-name)
                            :plan-invoices
                            api-ch
                            :context {:org-name org-name}))))
  (set-page-title! (str "Org settings - " org-name)))
