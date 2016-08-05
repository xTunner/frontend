(ns frontend.notifications
  (:require [frontend.utils :as utils :include-macros true]
            [goog.events]
            [frontend.utils.launchdarkly :as ld]))

(defn notifiable-browser? [] (exists? (.-Notification js/window)))
(defn notifications-permission [] (.-permission js/Notification))
(defn notifications-granted?  [] (= (notifications-permission) "granted"))

(defn request-permission
  ([]
   (request-permission (fn [result] (utils/mlog "Notifications are now: " result))))
  ([callback]
   (-> js/Notification
       (.requestPermission)
       (.then callback))))

(defn notification-on-click [event]
  (.preventDefault event)
  (.focus js/window))

;; Some notes about properties
;; - The title should be 32 characters MAX, note that if using system default,
;;   only 22 chars are visible on hover (because of resulting UI), so keep
;;   critical information 22 chars
;; - The body property should be a MAX of 42 characters (this is assuming a
;;   default text size with San Francisco, the default system font on macOS)
(defn notify [title properties]
  (when (and (notifications-granted?)
             (ld/feature-on? "web-notifications"))
    (goog.events/listen
      (new js/Notification
        title
        (clj->js (merge
                   properties
                   {:lang "en"})))
      "click"
      notification-on-click)))

(defn status-icon-path [status-name]
  (utils/cdn-path (str "/img/email/"status-name"/icon@3x.png")))

(defn notify-build-done [build]
  (let [status (:status build)
        project (:reponame build)
        build-num (:build_num build)
        properties (case status
                     "success" {:icon (status-icon-path "passed") :body "Yay, your tests passed!"}
                     "fixed" {:icon (status-icon-path "passed") :body "Yay, all your tests are fixed!"}
                     "no_tests" {:icon (status-icon-path "failed") :body "Looks like there were no tests to run."}
                     "failed" {:icon (status-icon-path "failed") :body "Looks like some tests failed."}
                     "infrastructure_fail" {:icon (status-icon-path "failed") :body "Darn, something went wrong."})]
    (notify (str project " #" build-num) (merge properties {:data build}))))
