(ns circleci.backend.build
  (:use [arohner.utils :only (inspect fold)])
  (:use [circleci.backend.action :only (validate-action-result!)])
  (:use [circleci.backend.action.bash :only (with-pwd)])
  (:use [circleci.backend.nodes :only (node-info)]))

(defrecord Build [project-name ;; string
                  build-num ;; int
                  actions ;; a seq of actions
                  action-results
                  group ;; the pallet group spec to use for the build
                  continue  ;; if true, continue running the build. Failed actions will set this to false
                  ])

(defn build [& {:keys [project-name build-num actions action-results group continue]
                :or {failed false}}]
  (Build. project-name build-num actions (or action-results []) group true))

(defrecord BuildContext [build
                         action
                         node])

(defn continue? [action-result]
  (or (-> action-result :success)
      (-> action-result :continue)))

(defn run-build [build]
  (println "getting node info")
  (let [node (-> build :group (node-info) (first))]
    (with-pwd "" ;; bind here, so actions can set! it
      (fold build [act (-> build :actions)]
        (if (-> build :continue)
          (let [context {:build build
                         :action act
                         :node node}
                _ (println "calling" (-> act :name))
                action-result (-> act :act-fn (.invoke context))]
            (println "result:" action-result)
            (validate-action-result! action-result)
            (-> build
                (update-in [:action-results] conj action-result)
                (update-in [:continue] (fn [_] (continue? action-result)))))
          build)))))