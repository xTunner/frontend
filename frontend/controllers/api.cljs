(ns frontend.controllers.api
  (:require [cljs.core.async :refer [put! close!]]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.models.repo :as repo-model]
            [frontend.routes :as routes]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :refer [mlog merror]]
            [goog.string :as gstring])
  (:require-macros [frontend.utils :refer [inspect]]))

;; when a button is clicked, the post-controls will make the API call, and the
;; result will be pushed into the api-channel
;; the api controller will do assoc-in
;; the api-post-controller can do any other actions

;; --- API Multimethod Declarations ---

(defmulti api-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; args is the 2nd value in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message status args state] [message status]))

(defmulti post-api-event!
  (fn [target message status args previous-state current-state] [message status]))

;; --- API Multimethod Implementations ---

(defmethod api-event :default
  [target message status args state]
  ;; subdispatching for state defaults
  (let [submethod (get-method api-event [:default status])]
    (if submethod
      (submethod target message status args state)
      (do (merror "Unknown api: " message args)
          state))))

(defmethod post-api-event! :default
  [target message status args previous-state current-state]
  ;; subdispatching for state defaults
  (let [submethod (get-method post-api-event! [:default status])]
    (if submethod
      (submethod target message status args previous-state current-state)
      (merror "Unknown api: " message status args))))

(defmethod api-event [:default :started]
  [target message status args state]
  ;; XXX Set the button to "saving" (is this the best place?)
  ;; XXX Start the spinner
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :started]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:default :success]
  [target message status args state]
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :success]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:default :failed]
  [target message status args state]
  ;; XXX update the error message
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :failed]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:default :finished]
  [target message status args state]
  ;; XXX Reset the button (is this the best place?)
  ;; XXX Stop the spinner
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :finished]
  [target message status args previous-state current-state]
  (mlog "No post-api for: " [message status]))


(defmethod api-event [:projects :success]
  [target message status args state]
  (mlog "projects success")
  (assoc-in state [:projects] (:resp args)))


(defmethod api-event [:recent-builds :success]
  [target message status args state]
  (mlog "recentbuilds success")
  (assoc-in state [:recent-builds] (:resp args)))


(defmethod api-event [:build :success]
  [target message status args state]
  (mlog "build success")
  (let [build (:resp args)
        {:keys [build-num project-name]} (:context args)
        containers (vec (build-model/containers build))]
    (if-not (and (= build-num (:build_num build))
                 (= project-name (vcs-url/project-name (:vcs_url build))))
      state
      (assoc-in state [:current-build] (-> build
                                           (assoc :containers containers)
                                           (dissoc :steps))))))

(defmethod post-api-event! [:build :success]
  [target message status args previous-state current-state]
  (let [{:keys [build-num project-name]} (:context args)]
    ;; This is slightly different than the api-event because we don't want to have to
    ;; convert the build from steps to containers again.
    (when (and (= build-num (get-in args [:resp :build_num]))
               (= project-name (vcs-url/project-name (get-in args [:resp :vcs_url]))))
      (doseq [action (mapcat :actions (get-in current-state [:current-build :containers]))
              :when (or (= "running" (:status action))
                        (action-model/failed? action))]
        ;; XXX: should this fetch the action logs itself creating controls events?
        (put! (get-in current-state [:comms :controls])
              [:action-log-output-toggled (select-keys action [:step :index])])))))


(defmethod api-event [:repos :success]
  [target message status args state]
  ;; prevent delayed api responses if the user has moved on
  (let [login (get-in args [:context :login])
        type (get-in args [:context :type])
        repo-key (str login "." type)]
    (assoc-in state [:current-user :repos repo-key] (:resp args))))


(defmethod api-event [:organizations :success]
  [target message status args state]
  (assoc-in state [:current-user :organizations] (:resp args)))


(defmethod api-event [:collaborators :success]
  [target message status args state]
  (assoc-in state [:current-user :collaborators] (:resp args)))


(defmethod api-event [:usage-queue :success]
  [target message status args state]
  (let [usage-queue-builds (:resp args)
        build-id (:context args)]
    (if-not (= build-id (-> state :current-build build-model/id))
      state
      (assoc-in state [:current-build :usage-queue-builds] usage-queue-builds))))


(defmethod api-event [:build-artifacts :success]
  [target message status args state]
  (let [artifacts (:resp args)
        build-id (:context args)]
    (if-not (= build-id (-> state :current-build build-model/id))
      state
      (assoc-in state [:current-build :artifacts] artifacts))))


(defmethod api-event [:action-log :success]
  [target message status args state]
  (let [action-log (:resp args)
        {:keys [index step]} (:context args)]
    (-> state
        (assoc-in [:current-build :containers index :actions step :output] action-log)
        (update-in [:current-build :containers index :actions step] action-model/format-all-output))))


(defmethod api-event [:project-settings :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (update-in state [:current-project] merge resp)))


(defmethod api-event [:project-plan :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (update-in state [:current-project] merge {:plan resp})))


(defmethod api-event [:project-token :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (update-in state [:current-project] merge {:tokens resp})))


(defmethod api-event [:project-envvar :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (update-in state [:current-project] merge {:env-vars resp})))


(defmethod api-event [:update-project-parallelism :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (assoc-in state [:current-project :parallelism-edited] true)))


(defmethod api-event [:create-env-var :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (-> state
        (update-in [:current-project :env-vars] (fnil conj []) resp)
        (assoc-in [:current-project :new-env-var-name] "")
        (assoc-in [:current-project :new-env-var-value] ""))))


(defmethod api-event [:delete-env-var :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (-> state
        (update-in [:current-project :env-vars] (fn [vars]
                                                  (remove #(= (:env-var-name context) (:name %))
                                                          vars))))))


(defmethod api-event [:save-ssh-key :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (assoc-in state [:current-project :new-ssh-key] {})))

(defmethod post-api-event! [:save-ssh-key :success]
  [target message status {:keys [context resp]} previous-state current-state]
  (when (= (:project-id context) (project-model/id (:current-project current-state)))
    (let [project-name (vcs-url/project-name (:project-id context))
          api-ch (get-in current-state [:comms :api])]
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/settings" project-name)
                  :project-settings
                  api-ch
                  :context {:project-name project-name}))))


(defmethod api-event [:delete-ssh-key :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (update-in state [:current-project :ssh_keys] (fn [keys]
                                                    (remove #(= (:fingerprint context) (:fingerprint %))
                                                            keys)))))


(defmethod api-event [:save-project-api-token :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (-> state
        (assoc-in [:current-project :new-api-token] {})
        (update-in [:current-project :tokens] (fnil conj []) resp))))


(defmethod api-event [:delete-project-api-token :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (update-in state [:current-project :tokens] (fn [tokens]
                                                  (remove #(= (:token %) (:token context))
                                                          tokens)))))


(defmethod api-event [:set-heroku-deploy-user :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (assoc-in state [:current-project :heroku_deploy_user] (:login context))))


(defmethod api-event [:remove-heroku-deploy-user :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (:current-project state)))
    state
    (assoc-in state [:current-project :heroku_deploy_user] nil)))


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
  (when (and (= (project-model/id (:current-project current-state))
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
