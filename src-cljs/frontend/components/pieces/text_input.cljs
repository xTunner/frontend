(ns frontend.components.pieces.text-input
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn text-input
  "<DOC STRING GOES here>."
  [{:keys [id value size disabled?] :or {:size "medium"}}]
  (component
    (html
    [:input {:type "text"
             :id id
             :defaultValue value
             :disabled disabled?
             ;; Ideally, we should remove our default styles and do *all*
             ;; styling in the component
             :class size
             }])))

(dc/do
  (defcard text-input
    (html
     [:div
      (text-input {:value "Regular little text input"})
      (text-input {:value "Medium sized text input"
                   :size "medium"})
      (text-input {:value "Large sized text input"
                   :size "large"})
      ])))
