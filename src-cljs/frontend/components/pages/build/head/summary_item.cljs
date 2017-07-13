(ns frontend.components.pages.build.head.summary-item
  (:require [frontend.utils :as utils :refer-macros [component html]]
            [om.core :as om :include-macros true]))

(defn- summary-item* [{:keys [label value tooltip]} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html
         [:.summary-item
          (when label
            [:span.summary-label
             label
             (when tooltip
               [:span.summary-tooltip
                tooltip])])
          value])))))

(defn summary-item [label value & [tooltip]]
  (om/build summary-item* {:label label
                           :value value
                           :tooltip tooltip}))
