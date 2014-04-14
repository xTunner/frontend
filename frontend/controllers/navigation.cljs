(ns frontend.controllers.navigation
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :refer [mlog]]))

(def from
  :navigation-point)

(defmulti navigated-to
  (fn [target to args state] to))

(defmethod navigated-to :default
  [target to args state]
  (mlog "Unknown nav event: " (pr-str to))
  state)

(defmethod navigated-to :root
  [target to [project-id build-num] state]
  (mlog "Navigated from " (from state) " to " to)
  (assoc state :navigation-point :dashboard))

(defmethod navigated-to :build-inspector
  [target to [project-id build-num] state]
  (assoc state :inspected-project {:project project-id
                                   :build-num build-num}
         :navigation-point :build))

(defmethod navigated-to :add-projects
  [target to [project-id build-num] state]
  (assoc state :navigation-point :add-projects))
