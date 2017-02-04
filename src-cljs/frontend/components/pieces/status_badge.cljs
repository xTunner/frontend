(ns frontend.components.pieces.status-badge
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.icon :as icon]
            [frontend.utils.devcards :as devcard-utils])
  (:require-macros [frontend.utils :refer [component html]]))

(defn- not-run-words [build]
  (case (:dont_build build)
    ("ci-skip" "branch-blacklisted" "branch-not-whitelisted") "skipped"
    ("org-not-paid" "user-not-paid") "not paid"
    "not run"))

(defn- status-words [build]
  (case (:status build)
    "infrastructure_fail" "circle bug"
    "timedout" "timed out"
    "no_tests" "no tests"
    "not_run" (not-run-words build)
    "not_running" "not running"
    (:status build)))

(defn- status-class [build]
  (cond (#{"failed" "timedout" "no_tests"} (:status build)) "fail"
        (= "success" (:outcome build)) "pass"
        (= "running" (:status build)) "busy"
        (#{"queued" "not_running" "scheduled"} (:status build)) "queued"

        (or
         (#{"infrastructure_fail" "killed" "not_run" "retried" "canceled"} (:status build))
         ;; If there's no build at all, consider that a "stop"-like status.
         (nil? build))
        "stop"

        :else nil))

(defn- status-icon [build]
  (case (status-class build)
    "fail" (icon/status-failed)
    "stop" (icon/status-canceled)
    "pass" (icon/status-passed)
    "busy" (icon/status-running)
    "queued" (icon/status-queued)))

(defn status-badge [build]
  (component
    (html
     [:div {:class (status-class build)}
      [:.status-icon (status-icon build)]
      [:.badge-text (status-words build)]])))

(dc/do
  (defcard status-badge
    (html
     [:div
      (for [build [{:status "not_running", :outcome nil, :lifecycle "not_running"}
                   {:status "running", :outcome nil, :lifecycle "running"}
                   {:status "success" :outcome "success", :lifecycle "finished"}
                   {:status "fixed", :outcome "success", :lifecycle "finished"}
                   {:status "failed" :outcome "failed", :lifecycle "finished"}
                   {:status "timedout", :outcome "timedout", :lifecycle "finished"}
                   {:status "canceled", :outcome "canceled", :lifecycle "finished"}
                   {:status "not_run", :outcome nil, :lifecycle "not_run"}]]
        (html
         [:div {:style {:display "flex"
                        :align-items "center"
                        :margin-bottom "20px"}}
          [:div {:style {:flex "0 0 auto"
                         :margin-right "10px"}}
           (status-badge build)]
          [:div {:style {:flex "1 1 0"}}
           (devcard-utils/display-data build)]]))])))
