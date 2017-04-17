(ns frontend.components.pages.add-projects
  (:require [frontend.components.add-projects :as add-projects]
            [frontend.components.templates.main :as main-template]
            [frontend.utils.launchdarkly :as ld]
            [om.core :as om :include-macros true]))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
       (merge {:app app
               :main-content (om/build add-projects/add-projects app)}
         (when-not (ld/feature-on? "top-bar-ui-v-1")
           {:header-actions (om/build add-projects/add-projects-head-actions app)}))))))
