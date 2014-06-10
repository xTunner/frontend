(ns frontend.controllers.post-navigation
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [clojure.string :as string]
            [frontend.utils :as utils :refer [mlog merror]]
            [frontend.pusher :as pusher]
            [frontend.state :as state]
            [goog.string :as gstring]
            [goog.string.format]))

(defn set-page-title! [& [title]]
  (set! (.-title js/document) (if title
                                (str title  " - CircleCI")
                                "CircleCI")))

(defmulti post-navigated-to!
  (fn [history-imp to args previous-state current-state] to))

(defmethod post-navigated-to! :default
  [history-imp to args previous-state current-state]
  (mlog "No post-nav for: " to))

(defmethod post-navigated-to! :navigate!
  [history-imp to path previous-state current-state]
  (let [path (if (= \/ (first path))
               (subs path 1)
               path)]
    (.setToken history-imp path)))

(defmethod post-navigated-to! :dashboard
  [history-imp to args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (utils/ajax :get "/api/v1/projects" :projects api-ch)
    (when-let [builds-url (cond (empty? args) "/api/v1/recent-builds"
                                (:branch args) (gstring/format "/api/v1/project/%s/%s/tree/%s"
                                                               (:org args) (:repo args) (:branch args))
                                (:repo args) (gstring/format "/api/v1/project/%s/%s"
                                                             (:org args) (:repo args))
                                (:org args) (gstring/format "/api/v1/organization/%s"
                                                            (:org args))
                                :else (merror "unknown path for %s" args))]
      (utils/ajax :get builds-url :recent-builds api-ch)))
  (set-page-title!))

;; XXX: add unsubscribe when you leave the build page
(defmethod post-navigated-to! :build-inspector
  [history-imp to [project-name build-num] previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        ws-ch (get-in current-state [:comms :ws])]
    (utils/ajax :get
                (gstring/format "/api/v1/project/%s/%s" project-name build-num)
                :build
                api-ch
                :context {:project-name project-name :build-num build-num})
    (when (not (get-in current-state state/project-path))
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/settings" project-name)
                  :project-settings
                  api-ch
                  :context {:project-name project-name}))
    (when (not (get-in current-state state/project-plan-path))
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/plan" project-name)
                  :project-plan
                  api-ch
                  :context {:project-name project-name}))
    (put! ws-ch [:subscribe {:channel-name (pusher/build-channel {:vcs_url (str "https://github.com/" project-name)
                                                                  :build_num build-num})
                             :messages pusher/build-messages}]))
  (set-page-title! (str project-name " #" build-num)))

(defmethod post-navigated-to! :add-projects
  [history-imp to args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (utils/ajax :get "/api/v1/user/organizations" :organizations api-ch)
    (utils/ajax :get "/api/v1/user/collaborator-accounts" :collaborators api-ch))
  (set-page-title! "Add projects"))

;; XXX: find a better place for all of the ajax functions, maybe a separate api
;;      namespace that knows about all of the api routes?
(defmethod post-navigated-to! :project-settings
  [history-imp to {:keys [project-name subpage]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (if (get-in current-state state/project-path)
      (mlog "project settings already loaded for" project-name)
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/settings" project-name)
                  :project-settings
                  api-ch
                  :context {:project-name project-name}))

    (cond (and (= subpage :parallel-builds)
               (not (get-in current-state state/project-plan-path)))
          (utils/ajax :get
                      (gstring/format "/api/v1/project/%s/plan" project-name)
                      :project-plan
                      api-ch
                      :context {:project-name project-name})

          (and (= subpage :api)
               (not (get-in current-state state/project-tokens-path)))
          (utils/ajax :get
                      (gstring/format "/api/v1/project/%s/token" project-name)
                      :project-token
                      api-ch
                      :context {:project-name project-name})

          (and (= subpage :env-vars)
               (not (get-in current-state state/project-envvars-path)))
          (utils/ajax :get
                      (gstring/format "/api/v1/project/%s/envvar" project-name)
                      :project-envvar
                      api-ch
                      :context {:project-name project-name})
          :else nil))

  ;; XXX: check for XSS
  (set-page-title! (str "Edit settings - " project-name)))
