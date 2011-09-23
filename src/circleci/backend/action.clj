(ns circleci.backend.action
  (:use [arohner.utils :only (inspect)]))

(defrecord Action [name
                   dependencies ;; a seq of actions to be called first
                   act-fn ;; an fn of one argument, the affected node. If returns falsy, the action has "failed" and the on-fail code is run
                   on-fail ;; what to do when the action fails. A keyword, either :continue or :abort
                   ])

(defrecord ActionResult [status
                         failure-recommendation])
(defn action
  "defines an action."
  [name & {:keys [dependencies act-fn on-fail]}]
  (Action. name dependencies act-fn (or on-fail :abort)))

(defn invoke-action [act node]
  (let [response ((-> act :act-fn) node)]
    (if response
      (ActionResult. :success nil)
      (ActionResult. :failure (-> act :on-fail)))))

(defmacro def-action [name ])