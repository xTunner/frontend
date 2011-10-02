(ns circleci.backend.action.junit
  (:require [circleci.backend.action :as action])
  (:require [circleci.backend.action.transfer :as transfer]))

(defn with-junit [action junit-path]
  (update-in action [:act-fn] (constantly
                               (fn [context]
                                 (if-let [resp (-> action :act-fn (.invoke context))]
                                   (if (action/continue? resp)
                                     
                                     resp))))))

(defn junit [junit-path]
  (action/action
   :act-fn (fn [context]
             (transfer/get-files context
                                 (transfer/find-files context
                                                      circleci.backend.action.bash/*pwd*
                                                      junit-path)
                                 (fn [instream]
                                   (println "with-junit:" (slurp instream)))))))