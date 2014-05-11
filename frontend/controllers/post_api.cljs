(ns frontend.controllers.post-api
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.intercom :as intercom]
            [frontend.models.repo :as repo-model]
            [frontend.routes :as routes]
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

(defmethod post-api-event! [:save-dependencies-commands :success]
  [target message status {:keys [context resp]} previous-state current-state]
  (let [nav-ch (get-in current-state [:comms :nav])
        org-id (vcs-url/org-name (:project-id context))
        repo-id (vcs-url/repo-name (:project-id context))]
    (put! nav-ch [:navigate! (routes/v1-project-settings-subpage {:org-id org-id
                                                                  :repo-id repo-id
                                                                  :subpage "tests"})])))
