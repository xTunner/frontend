(ns frontend.controllers.post-navigation
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.utils :as utils :refer [mlog merror]]
            [frontend.pusher :as pusher]
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
  (.setToken history-imp path))

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
                api-ch)
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
