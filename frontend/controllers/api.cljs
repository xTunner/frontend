(ns frontend.controllers.api
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [cljs.core.async :refer [put!]]
            [frontend.models.build :as build-model]
            [frontend.utils :as utils :refer [mlog mwarn merror]])
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
  (assoc-in state [:current-build] (:resp args)))

(defmethod api-event [:retry-build :started]
  [target message status {:keys [build-id]} state]
  (update-in state [:current-build] (fn [b] (if-not (= build-id (build-model/id b))
                                              b
                                              (assoc b :retry-state :started)))))

(defmethod api-event [:retry-build :success]
  [target message status data state]
  (assoc-in state [:current-build] data))

(defmethod api-event [:retry-build :finished]
  [target message status {:keys [build-id]} state]
  (update-in state [:current-build] (fn [b] (if-not (= build-id (build-model/id b))
                                              b
                                              (dissoc b :retry-state)))))

(defmethod api-event [:retry-build :finished]
  [target message status {:keys [build-id]} state]
  (update-in state [:current-build] (fn [b] (if-not (= build-id (build-model/id b))
                                              b
                                              (dissoc b :retry-state)))))

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
    (assoc-in state [:current-build :steps step :actions index :output] action-log)))
