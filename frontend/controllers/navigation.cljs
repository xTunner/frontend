(ns frontend.controllers.navigation
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.state :as state]
            [frontend.utils.state :as state-utils]
            [frontend.utils :as utils :refer [mlog]]
            [frontend.utils.vcs-url :as vcs-url]))

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
  (-> state
      (assoc :navigation-point :dashboard)
      state-utils/reset-current-build))

(defmethod navigated-to :build-inspector
  [history-imp to [project-name build-num] state]
  (-> state
      (assoc :inspected-project {:project project-name
                                 :build-num build-num}
             :navigation-point :build
             :project-settings-project-name project-name)
      state-utils/reset-current-build
      (#(if (state-utils/stale-current-project? % project-name)
          (state-utils/reset-current-project %)
          %))))

(defmethod navigated-to :add-projects
  [history-imp to [project-id build-num] state]
  (assoc state :navigation-point :add-projects))

(defmethod navigated-to :project-settings
  [history-imp to {:keys [project-name subpage]} state]
  (-> state
      (assoc :navigation-point :project-settings)
      (assoc :project-settings-subpage subpage)
      (assoc :project-settings-project-name project-name)
      (#(if (state-utils/stale-current-project? % project-name)
          (state-utils/reset-current-project %)
          %))))
