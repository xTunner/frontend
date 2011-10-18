(ns circle.backend.action
  (:use [arohner.validation :only (validate!)]
        [arohner.predicates :only (bool?)])
  (:use [circle.utils.except :only (throw-if-not)]))

(defrecord Action [name
                   dependencies ;; a seq of actions to be called first
                   act-fn ;; an fn of one argument, the session. If returns falsy, the action has "failed" and the on-fail code is run
                   on-fail ;; what to do when the action fails. A keyword, either :continue or :abort
                   ])

(defrecord ActionResult [name ;;mandatory
                         success  ;; boolean, mandatory
                         continue ;; boolean, mandatory
                         out  ;; stdout from the command, a string (optional)
                         err  ;; stderr from the command, a string (optional)
                         exit ;; exit status from the command (optional)
                         ])

(def ActionResult-validator
  [[map? "result must be a map"
    :action-name "result must have a name"
    #(-> % :success bool?) ":success must be a bool, got %s" #(-> % :success (class) )]
   [#(or (-> % :success) (-> % :continue bool?)) ":continue must be a bool, got %s" #(-> % :continue (class))]])

(defn validate-action-result! [ar]
  (validate! ActionResult-validator ar))

(defn action*
  "defines an action."
  [& {:keys [name dependencies act-fn on-fail]}]
  (Action. name dependencies act-fn (or on-fail :abort)))

(defn successful? [action-result]
  (-> action-result :success))

(defn continue? [action-result]
  (or (-> action-result :success)
      (-> action-result :continue)))

(defn action-fn
  "Takes a fn of one argument, the context. returns an action-fn that validates, and captures stdout. Context is the name of the context variable"
  [action-name f]
  (fn [context]
    (let [result (atom nil)
          out-str ;;with-out-str
          (swap! result (fn [_]
                          (f context)))]
      (println "action-name=" action-name)
      (swap! result (fn [r]
                      (merge {:name action-name} r)))
      (swap! result (fn [r]
                      (merge-with str r {:out out-str})))
      (validate-action-result! @result)
      @result)))

(defn action [name f]
  (action*
   :name name
   :act-fn (action-fn name f)))

(defmacro defaction [name defn-args action-map f]
  (throw-if-not (vector? defn-args) "defn args must be a vector")
  (throw-if-not (map? action-map) "action-map must be a map")
  `(defn ~name ~defn-args
     (let [act-map# ~action-map
           act-name# (or (:name act-map#) (quote ~name))
           f# ~f]
       (throw-if-not (fn? f#) "f must be a fn")
       (action*
        :name act-name#
        :act-fn (action-fn act-name# f#)))))
