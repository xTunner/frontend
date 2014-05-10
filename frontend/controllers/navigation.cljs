(ns frontend.controllers.navigation
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :refer [mlog]]))

(def from
  :navigation-point)

(defmulti navigated-to
  (fn [history-imp to args state] to))

(defmethod navigated-to :default
  [history-imp to args state]
  (mlog "Unknown nav event: " (pr-str to))
  state)

(defmethod navigated-to :dashboard
  [history-imp to args state]
  (mlog "Navigated from " (from state) " to " to)
  (assoc state :navigation-point :dashboard :current-builds nil))

(defmethod navigated-to :build-inspector
  [history-imp to [project-name build-num] state]
  (assoc state
    :inspected-project {:project project-name
                        :build-num build-num}
    :navigation-point :build
    :current-build nil))

(defmethod navigated-to :add-projects
  [history-imp to [project-id build-num] state]
  (assoc state :navigation-point :add-projects))
