(ns frontend.controllers.api
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [cljs.core.async :refer [put!]]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.state :as state]
            [frontend.utils :as utils :refer [mlog mwarn merror]]
            [frontend.utils.vcs-url :as vcs-url])
  (:require-macros [frontend.utils :refer [inspect]]))

;; when a button is clicked, the post-controls will make the API call, and the
;; result will be pushed into the api-channel
;; the api controller will do assoc-in
;; the api-post-controller can do any other actions

(defmulti api-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; args is the 2nd value in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message status args state] [message status]))

(defmethod api-event :default
  [target message status args state]
  ;; subdispatching for state defaults
  (let [submethod (get-method api-event [:default status])]
    (if submethod
      (submethod target message status args state)
      (do (merror "Unknown api: " message args)
          state))))

(defmethod api-event [:default :started]
  [target message status args state]
  ;; XXX Set the button to "saving" (is this the best place?)
  ;; XXX Start the spinner
  (mlog "No api for" [message status])
  state)

(defmethod api-event [:default :finished]
  [target message status args state]
  ;; XXX Reset the button (is this the best place?)
  ;; XXX Stop the spinner
  (mlog "No api for" [message status])
  state)

(defmethod api-event [:default :success]
  [target message status args state]
  (mlog "No api for" [message status])
  state)

(defmethod api-event [:default :failed]
  [target message status args state]
  ;; XXX update the error message
  (mlog "No api for" [message status])
  state)

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
      (-> state
          (assoc-in state/build-path build)
          (assoc-in state/containers-path containers)))))

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
    (if-not (= build-id (build-model/id (get-in state state/build-path)))
      state
      (assoc-in state state/usage-queue-path usage-queue-builds))))

(defmethod api-event [:build-artifacts :success]
  [target message status args state]
  (let [artifacts (:resp args)
        build-id (:context args)]
    (if-not (= build-id (build-model/id (get-in state state/build-path)))
      state
      (assoc-in state state/artifacts-path artifacts))))

(defmethod api-event [:action-log :success]
  [target message status args state]
  (let [action-log (:resp args)
        {action-index :step container-index :index} (:context args)]
    (-> state
        (assoc-in (state/action-output-path container-index action-index) action-log)
        (update-in (state/action-path container-index action-index) action-model/format-all-output))))

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
