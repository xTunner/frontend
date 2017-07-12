(ns frontend.controllers.api.impl
  (:require [frontend.async :as front.async]
            [frontend.utils :as utils]))

;; when a button is clicked, the post-controls will make the API call, and the
;; result will be pushed into the api-channel
;; the api controller will do assoc-in
;; the api-post-controller can do any other actions

;; --- API Multimethod Declarations ---

(defmulti
  api-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; args is the 2nd value in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message status args state]
    [message status]))

(defmulti
  post-api-event!
  (fn [target message status args previous-state current-state comms]
    [message status]))

;; --- API Multimethod Defaults ---

(defmethod api-event :default
  [target message status args state]
  ;; subdispatching for state defaults
  (let [submethod (get-method api-event [:default status])]
    (if submethod
      (submethod target message status args state)
      (do (utils/merror "Unknown api: " message args)
          state))))

(defmethod post-api-event! :default
  [target message status args previous-state current-state comms]
  ;; subdispatching for state defaults
  (let [submethod (get-method post-api-event! [:default status])]
    (if submethod
      (submethod target message status args previous-state current-state)
      (utils/merror "Unknown api: " message status args))))

(defmethod api-event [:default :started]
  [target message status args state]
  (utils/mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :started]
  [target message status args previous-state current-state comms]
  (utils/mlog "No post-api for: " [message status]))

(defmethod api-event [:default :success]
  [target message status args state]
  (utils/mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :success]
  [target message status args previous-state current-state comms]
  (utils/mlog "No post-api for: " [message status]))

(defmethod api-event [:default :failed]
  [target message status args state]
  (utils/mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :failed]
  [target message status args previous-state current-state comms]
  (front.async/put! (:errors comms) [:api-error args])
  (utils/mlog "No post-api for: " [message status]))

(defmethod api-event [:default :finished]
  [target message status args state]
  (utils/mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :finished]
  [target message status args previous-state current-state comms]
  (utils/mlog "No post-api for: " [message status]))
