(ns frontend.components.pieces.status-badge
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.common :as common]
            [frontend.components.svg :as svg]
            [frontend.utils.devcards :as devcard-utils]
            [om.core :as om])
  (:require-macros [frontend.utils :refer [component html]]))

(defn not-run-words [build]
  (case (:dont_build build)
    "ci-skip"            "skipped"
    "branch-blacklisted" "skipped"
    "branch-not-whitelisted" "skipped"
    "org-not-paid"       "not paid"
    "user-not-paid"      "not paid"
    "not run"))

(defn status-words [build]
  (condp = (:status build)
    "infrastructure_fail" "circle bug"
    "timedout" "timed out"
    "no_tests" "no tests"
    "not_run" (not-run-words build)
    "not_running" "not running"
    (:status build)))

(defn build-status-badge-wording [build]
  (let [wording       (status-words build)
        too-long?     (> (count wording) 10)]
    [:div {:class (if too-long?
                    "badge-text small-text"
                    "badge-text")}
     wording]))

(defn status-class [build]
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

(defn status-icon [build]
  (case (status-class build)
    "fail" "Status-Failed"
    "stop" "Status-Canceled"
    "pass" "Status-Passed"
    "busy" "Status-Running"
    "queued" "Status-Queued"
    nil))

(defn status-badge [build]
  (component
    (html
     [:div.recent-status-badge {:class (status-class build)}
      (om/build svg/svg {:class "badge-icon"
                         :src (-> build status-icon common/icon-path)})
      (build-status-badge-wording build)])))

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
