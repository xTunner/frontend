(ns circle.backend.action
  (:use [arohner.validation :only (validate!)]
        [arohner.predicates :only (bool?)]))

(defrecord Action [name
                   dependencies ;; a seq of actions to be called first
                   act-fn ;; an fn of one argument, the session. If returns falsy, the action has "failed" and the on-fail code is run
                   on-fail ;; what to do when the action fails. A keyword, either :continue or :abort
                   ])

(defrecord ActionResult [success  ;; boolean, mandatory
                         continue ;; boolean, mandatory
                         out  ;; stdout from the command, a string (optional)
                         err  ;; stderr from the command, a string (optional)
                         exit ;; exit status from the command (optional)
                         ])

(def ActionResult-validator
  [[#(-> % :success bool?) ":success must be a bool, got %s" #(-> % :success (class) )]
   [#(-> % :continue bool?) ":continue must be a bool, got %s" #(-> % :continue (class))]])

(defn validate-action-result! [ar]
  (validate! ActionResult-validator ar))

(defn action
  "defines an action."
  [& {:keys [name dependencies act-fn on-fail]}]
  (Action. name dependencies act-fn (or on-fail :abort)))

(defn successful? [action-result]
  (-> action-result :success))

(defn continue? [action-result]
  (or (-> action-result :success)
      (-> action-result :continue)))

