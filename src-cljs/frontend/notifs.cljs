(ns frontend.notifs
  (:require [frontend.utils :as utils :include-macros true]
            [frontend.state :as state]
            [frontend.localstorage :as lost]))

(defn notifiable-browser [] (= (type (.-Notification js/window)) js/Function))

(defn notifications-granted?  [] (= (.-permission js/Notification) "default"))

;; Gotta edit this to only happen when a user first toggles that switch
;; This should actually be a little function called n/notify  that simply constructs a notification out of optional keyword arguments! (should have multiple signatures so if you just pass 1 arg it become the title)
(defn request-permission
  ([]
   (request-permission (fn [result] (.log js/console "Notifications are now: " result))))
  ([promise-fn]
     (-> js/Notification
               (.requestPermission)
               (.then promise-fn))))

;; Some notes about properties
;; The title should be 32 characters MAX, note that if using system default, only 22 chars are visible on hover (because of resulting UI), so keep critical information 22 chars
;; The body property should be a MAX of 42 characters (this is assuming a default text size with San Francisco, the default system font on macOS)
(defn notify [title properties]
  (def new-notif (new js/Notification
                      title
                      (clj->js (merge
                                 properties
                                 {:lang "en"})))))

(defn ask-then-notify [title properties]
  (request-permission
    (fn [status]
      (notify title properties))))

(defn email-path [status-name]
  (utils/cdn-path (str "/img/email/"status-name"/icon@3x.png")))

(defn notify-build-done [build]
  (let [status (:status build)
        project (:reponame build)
        build-num (:build_num build)
        properties (case status
                     "no_tests" {:icon (email-path "passed") :body "Looks like there were no tests to run."}
                     "success" {:icon (email-path "passed") :body "Yay, your tests passed!"}
                     "fixed" {:icon (email-path "passed") :body "Yay, all your tests are fixed!"}
                     "failed" {:icon (email-path "failed") :body "Looks like some tests failed."}
                     "infrastructure_fail" {:icon (email-path "failed") :body "Darn, something went wrong."}
                     {:icon (email-path "failed") :body "Whoops, no status inforamtion."})]
    (notify (str project " #"build-num) (merge properties {:data build}))
    (.log js/console (str build))
    ))
