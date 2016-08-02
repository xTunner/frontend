(ns frontend.components.pieces.text-input
  (:require [devcards.core :as dc :refer-macros [defcard]]
<<<<<<< 7a55a159aa37c37eb42b8f8f297f1f6c2d874a30
            [clojure.string :as str]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn log-text-output [item]
  (let [item-text (.-value (.-target item))]
    (.log js/console item-text)))

(defn text-input
  "<DOC STRING GOES here>."
  [{:keys [input-type on-change id value defaultValue size error? long? disabled? required?] :or {:input-type "text"
                                                                                     :size "medium"}}
   owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html
          [:input {:type input-type
                   :on-change on-change
                   :id id
                   :value value
                   :defaultValue value
                   :required required?
                   :disabled disabled?
                   :class (str/join " "
                                (list
                                  size
                                  (when error? "error")
                                  (when long? "long")))}])))))
=======
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
>>>>>>> Add basic modal components

(dc/do
  (defcard text-input
    (html
<<<<<<< 7a55a159aa37c37eb42b8f8f297f1f6c2d874a30
      [:div
       (om/build text-input {:value "Regular little text input"
                             :on-change log-text-output})
       (om/build text-input {:value "Medium sized text input"
                             :size "medium"
                             :on-change log-text-output})
       (om/build text-input {:value "Large sized text input"
                             :size "large"
                             :on-change log-text-output})]))
  (defcard errored-input
    (om/build text-input {:value "error@@circleci.com"
                          :type "email"
                          :error? true})))
=======
     [:div
      (text-input {:value "Regular little text input"})
      (text-input {:value "Medium sized text input"
                   :size "medium"})
      (text-input {:value "Large sized text input"
                   :size "large"})
      ])))
>>>>>>> Add basic modal components
