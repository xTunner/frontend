(ns frontend.test-utils
  (:require [clojure.string :as string]
            [om.core :as om :include-macros true]
            [om.dom]
            [cemerick.cljs.test :as test]
            [goog.dom]
            [secretary.core :as secretary]
            [cljs.core.async :as async]
            [frontend.routes :as routes])
  (:require-macros [frontend.utils :refer [inspect]]))

(def test-utils js/React.addons.TestUtils)

(defn simulate [key component-or-node & [event-data]]
  (let [simulate-obj (.-Simulate test-utils)]
    (.call (aget simulate-obj (name key)) simulate-obj component-or-node event-data)))

(defn render-into-document [component]
  (. test-utils (renderIntoDocument component)))

(defn component->content [component args]
  (-> (om/build component args)
      (om.dom/render-to-str)
      (goog.dom/htmlToDocumentFragment)
      (.-innerText)))

(def ^:dynamic *nav-ch*)

(defn with-routes-defined
  "Test middleware that sets up the secretary routes."
  ;; TODO: maybe we should do something fancy with mocking the app state here?
  [run-test]
  (secretary/reset-routes!)
  (binding [*nav-ch* (async/chan)]
    (routes/define-user-routes! *nav-ch* true)
    (routes/define-spec-routes! *nav-ch*)
    (let [results (run-test)]
      (secretary/reset-routes!)
      results)))

;;(defn render-into-document [ommish app-state]
;;  (om/build ommish app-state))

;; (defn mock-component)
;; (defn is-component-of-type)
;; (defn is-dom-component)
;; (defn is-composite-component)
;; (defn is-composite-component-with-type)
;; (defn is-text-component)
;; (defn find-all-in-rendered-tree)
;; (defn scry-rendered-dom-components-with-class)
;; (defn find-rendered-dom-component-with-class)
;; (defn scry-rendered-dom-components-with-tag)
;; (defn find-rendered-dom-component-with-tag)
;; (defn scry-rendered-components-with-type)
;; (defn find-rendered-component-with-type)
