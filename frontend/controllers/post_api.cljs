(ns frontend.controllers.post-api
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.intercom :as intercom]
            [frontend.utils :as utils :refer [mlog]]))

(defmulti post-api-event!
  (fn [target message args previous-state current-state] message))

(defmethod post-api-event! :default
  [target message args previous-state current-state]
  (mlog "No post-api for: " message))

(defmethod post-api-event! :intercom-dialog-raised
  [target message dialog-message previous-state current-state]
  (intercom/raise-dialog (get-in current-state [:comms :errors]) dialog-message))

(defmethod post-api-event! :intercom-user-inspected
  [target message criteria previous-state current-state]
  (if-let [url (intercom/user-link)]
    (js/window.open url)
    (print "No matching url could be found from current window.location.pathname")))

(defmethod post-api-event! [:retry-build :finished]
  [target message {:keys [build-id]} previous-state current-state]
  (print (-> previous-state :current-build :retry-state) " => "
         (-> current-state :current-build :retry-state)))
