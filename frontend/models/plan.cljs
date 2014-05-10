(ns frontend.models.plan
  (:require [goog.string :as gstring]))

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
