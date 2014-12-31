(ns frontend.sample-test
  (:require [cemerick.cljs.test :as t]
            [frontend.utils :as utils]
            [frontend.test-utils :as util]
            [goog.dom]
            [sablono.core :as html :refer-macros [html]]
            [om.core :as om :include-macros true])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)]
                   [frontend.utils :refer [inspect]]))

(defn sample-component [data owner]
  (reify
    om/IRender
    (render [_]
      (html [:div "Woot"]))))

(deftest sample
  (let [n (goog.dom/htmlToDocumentFragment "<div class='sample-node'></div>")]
    (om/root sample-component (atom {}) {:target n})
    (is (= "Woot" (utils/text n)))))

(defn simulated-event-component [data owner]
  (reify
    om/IRender
    (render [_]
      (html [:button {:id "test-button" :onClick #(om/update! data :clicked true)}]))))

(deftest simulated-event
  (let [state (atom { :clicked false })
        node (.createElement js/document "div")]
    (om/root simulated-event-component state {:target node})
    (util/simulate :click (.-firstChild node) {})
    (is (:clicked @state))))
