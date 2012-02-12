(ns circle.util.retry
  (:use [circle.util.except :only (throw-if-not)])
  (:use [robert.bruce :only (try-try-again)]))

(defn parse-args [args]
  (if (map? (first args))
    {:options (first args)
     :f (second args)
     :args (drop 2 args)}
    {:f (first args)
     :args (rest args)}))

(defn wait-for
  "Like robert bruce, but waits for arbitrary results rather than just
  exceptions.

  Takes all arguments exactly the same as robert bruce. Extra arguments to wait-for may be passed in the robert bruce options:

 - success-fn: a fn of one argument, the return value of f. Stop retrying if success-fn returns truthy. If not specified, wait-for returns when f returns truthy"

  {:arglists
   '([fn] [fn & args] [options fn] [options fn & args])}
  [& args]
  (let [{:keys [options f args] :as parsed-args} (parse-args args)
        success (-> options :success-fn)]
    (throw-if-not (-> parsed-args :f fn?) "couldn't find fn")
    (let [f (fn []
              (let [result (apply f args)]
                (if success
                  (throw-if-not (success result) "(success) did not return truthy in time")
                  (throw-if-not result "f did not return truthy in time"))
                result))]
      (try-try-again options f))))