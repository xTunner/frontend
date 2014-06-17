(ns frontend.models.plan
  (:require [goog.string :as gstring]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]))

(defn max-parallelism
  "Maximum parallelism that the plan allows (usually 16x)"
  [plan]
  (get-in plan [:template_properties :max_parallelism]))

(defn usable-containers
  "Maximum containers that the plan has available to it"
  [plan]
  (max (:containers_override plan) (:containers plan) (get-in plan [:template_properties :free_containers])))

(defn max-selectable-parallelism [plan]
  (min (max-parallelism plan)
       (usable-containers plan)))


(defn piggieback? [plan]
  (and (contains? (keys plan) :current_org_name)
       (contains? (keys plan) :org_name)
       (not= (:current_org_name plan) (:org_name plan))))

(defn paid? [plan]
  (not= (get-in plan [:template_properties :type] "trial") "trial"))

(defn can-edit-plan? [plan]
  (and (paid? plan) (not (piggieback? plan))))

(defn trial? [plan]
  (some-> plan :template-properties :type name (= "trial")))

(defn trial-over? [plan]
  (time/after? (time/now) (time-format/parse (:trial_end plan))))

(defn days-left-in-trial
  "Returns number of days left in trial, can be negative."
  [plan]
  (let [trial-end (time-format/parse (:trial_end plan))
        now (time/now)]
    (if (time/after? trial-end now)
      ;; count partial days as a full day
      (inc (time/in-days (time/interval now trial-end)))
      (- (time/in-days (time/interval trial-end now))))))
