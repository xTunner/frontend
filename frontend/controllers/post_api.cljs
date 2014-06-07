(ns frontend.controllers.post-api
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.intercom :as intercom]
            [frontend.models.action :as action-model]
            [frontend.models.project :as project-model]
            [frontend.models.repo :as repo-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils.mixpanel :as mixpanel]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :refer [mlog merror]]
            [goog.string :as gstring]
            goog.string.format)
  (:require-macros [frontend.utils :refer [inspect]]))

(defmulti post-api-event!
  (fn [target message status args previous-state current-state] [message status]))

(defmethod post-api-event! :default
  [target message status args previous-state current-state]
  ;; subdispatching for state defaults
  (let [submethod (get-method post-api-event! [:default status])]
    (if submethod
      (submethod target message status args previous-state current-state)
      (merror "Unknown api: " message status args))))

(defmethod post-api-event! [:default :started]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod post-api-event! [:default :success]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod post-api-event! [:default :failed]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod post-api-event! [:default :finished]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod post-api-event! [:build :success]
  [target message status args previous-state current-state]
  (let [{:keys [build-num project-name]} (:context args)]
    ;; This is slightly different than the api-event because we don't want to have to
    ;; convert the build from steps to containers again.
    (when (and (= build-num (get-in args [:resp :build_num]))
               (= project-name (vcs-url/project-name (get-in args [:resp :vcs_url]))))
      (doseq [action (mapcat :actions (get-in current-state state/containers-path))
              :when (or (= "running" (:status action))
                        (action-model/failed? action))]
        ;; XXX: should this fetch the action logs itself creating controls events?
        (put! (get-in current-state [:comms :controls])
              [:action-log-output-toggled (select-keys action [:step :index])])))))

(defmethod post-api-event! [:followed-repo :success]
  [target message status args previous-state current-state]
  (js/_gaq.push ["_trackEvent" "Repos" "Add"])
  (if-let [first-build (get-in args [:resp :first_build])]
    (let [nav-ch (get-in current-state [:comms :nav])
          build-path (-> first-build
                         :build_url
                         (goog.Uri.)
                         (.getPath)
                         (subs 1))]
      (put! nav-ch [:navigate! build-path]))
    (when (repo-model/should-do-first-follower-build? (:context args))
      (utils/ajax :post
                  (gstring/format "/api/v1/project/" (vcs-url/project-name (:vcs_url (:context args))))
                  :start-build
                  (get-in current-state [:comms :api])))))

(defmethod post-api-event! [:start-build :success]
  [target message status args previous-state current-state]
  (let [nav-ch (get-in current-state [:comms :nav])
        build-url (-> args :resp :build_url (goog.Uri.) (.getPath) (subs 1))]
    (put! nav-ch [:navigate! build-url])))

(defmethod post-api-event! [:retry-build :success]
  [target message status args previous-state current-state]
  (let [nav-ch (get-in current-state [:comms :nav])
        build-url (-> args :resp :build_url (goog.Uri.) (.getPath) (subs 1))]
    (put! nav-ch [:navigate! build-url])))

(defmethod post-api-event! [:save-dependencies-commands :success]
  [target message status {:keys [context resp]} previous-state current-state]
  (when (and (= (project-model/id (get-in current-state state/project-path))
                (:project-id context))
             (= :setup (:project-settings-subpage current-state)))
    (let [nav-ch (get-in current-state [:comms :nav])
          org-id (vcs-url/org-name (:project-id context))
          repo-id (vcs-url/repo-name (:project-id context))]
      (put! nav-ch [:navigate! (routes/v1-project-settings-subpage {:org-id org-id
                                                                    :repo-id repo-id
                                                                    :subpage "tests"})]))))


(defmethod post-api-event! [:save-test-commands-and-build :success]
  [target message status {:keys [context resp]} previous-state current-state]
  (let [controls-ch (get-in current-state [:comms :controls])]
    (put! controls-ch [:started-edit-settings-build context])))

(defmethod post-api-event! [:save-ssh-key :success]
  [target message status {:keys [context resp]} previous-state current-state]
  (when (= (:project-id context) (project-model/id (get-in current-state state/project-path)))
    (let [project-name (vcs-url/project-name (:project-id context))
          api-ch (get-in current-state [:comms :api])]
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/settings" project-name)
                  :project-settings
                  api-ch
                  :context {:project-name project-name}))))

(defmethod post-api-event! [:first-green-build-github-users :success]
  [target message status {:keys [resp context]} previous-state current-state]
  ;; This is not ideal, but don't see a better place to put this
  (when (first (remove :following resp))
    (mixpanel/track "Saw invitations prompt" {:first_green_build true
                                              :project (:project-name context)})))
