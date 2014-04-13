(ns frontend.controllers.api
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [cljs.core.async :refer [put!]]
            [ajax.core :as ajax]
            [frontend.models.build :as build-model]
            [frontend.utils :as utils :refer [mlog mwarn merror]])
  (:require-macros [frontend.utils :refer [inspect]]))

;; when a button is clicked, the post-controls will make the API call, and the
;; result will be pushed into the api-channel
;; the api controller will do assoc-in
;; the api-post-controller can do any other actions

(defn ajax [method url message channel & {:keys [params format response-format keywords? context]
                                          :or {params {}
                                               format :json
                                               response-format :json
                                               keywords? true}}]
  (put! channel [message :started context])
  (ajax/ajax-request url method
                     (ajax/transform-opts
                      {:format format
                       :response-format response-format
                       :keywords? keywords?
                       :params params
                       :headers {:Accept "application/json"}
                       :handler #(put! channel [message :success {:resp %
                                                                  :context context}])
                       :error-handler #(put! channel [message :failed {:resp %
                                                                       :context context}])
                       :finally #(put! channel [message :finished context])})))


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
  (mlog "Started: " message)
  state)

(defmethod api-event [:default :finished]
  [target message status args state]
  ;; XXX Reset the button (is this the best place?)
  ;; XXX Stop the spinner
  (mlog "Finished: " message)
  state)

(defmethod api-event [:default :success]
  [target message status args state]
  (merror "Unhandled successful api: " message)
  state)

(defmethod api-event [:default :failed]
  [target message status args state]
  ;; XXX update the error message
  (mlog "Failed: " message args)
  state)

(defmethod api-event [:projects :success]
  [target message status args state]
  (mlog "projects success")
  (assoc-in state [:projects] (:resp args)))

(defmethod api-event [:recent-builds :success]
  [target message status args state]
  (mlog "recentbuilds success")
  (assoc-in state [:recent-builds] (:resp args)))

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

(defmethod api-event [:repos :started]
  [target message status args state]
  (dissoc state :current-repos))

(defmethod api-event [:repos :success]
  [target message status args state]
  ;; prevent delayed api responses if the user has moved on
  (if (= (get-in state [:settings :add-projects :selected-org])
         (get-in args [:context :org-name]))
    (assoc state :current-repos (:resp args))
    (do
      (println "skipping update, user selected another org")
      state)))
