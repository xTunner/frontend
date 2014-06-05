(ns frontend.controllers.navigation
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.state :as state]
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
      state/reset-current-build))

(defmethod navigated-to :build-inspector
  [history-imp to [project-name build-num] state]
  (-> state
      (assoc :inspected-project {:project project-name
                                 :build-num build-num}
             :navigation-point :build)
      state/reset-current-build))

(defmethod navigated-to :add-projects
  [history-imp to [project-id build-num] state]
  (assoc state :navigation-point :add-projects))

(defmethod navigated-to :project-settings
  [history-imp to {:keys [project-name subpage]} state]
  (-> state
      (assoc :navigation-point :project-settings)
      (assoc :project-settings-subpage subpage)
      (assoc :project-settings-project-name project-name)
      (#(if (and (:current-project state)
                 ;; XXX: check for url-escaped characters (e.g. /)
                 (not= project-name (vcs-url/project-name (get-in state [:current-project :vcs_url]))))
          (dissoc % :current-project)
          %))))
