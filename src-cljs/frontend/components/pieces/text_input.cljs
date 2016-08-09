(ns frontend.components.pieces.text-input
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [clojure.string :as str]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn log-text-output [item]
  (let [item-text (.-value (.-target item))]
    (.log js/console item-text)))

(defn text-input
  "<DOC STRING GOES here>."
  [{:keys [input-type on-change id value defaultvVlue size error? disabled? required?] :or {:input-type "text"
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
                                  (when error? "error")))}])))))

(dc/do
  (defcard text-input
    (html
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
