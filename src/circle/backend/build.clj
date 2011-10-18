(ns circle.backend.build
  (:use [arohner.utils :only (inspect fold)])
  (:use [circle.utils.except :only (throw-if-not)])
  (:use [circle.backend.action :only (continue? validate-action-result!)])
  (:use [circle.backend.action.bash :only (with-pwd)])
  (:use [circle.backend.nodes :only (node-info)]))

(defrecord Build [project-name ;; string
                  build-num ;; int
                  vcs-type
                  vcs-url
                  vcs-revision ;; if present, the commit that caused the build to be run, or nil
                  actions ;; a seq of actions
                  action-results
                  group ;; the pallet group spec to use for the build
                  num-nodes ;; number of nodes to start/use
                  lb-name   ;; name of the load-balancer to use
                  continue  ;; if true, continue running the build. Failed actions will set this to false
                  ])

(defn build [& {:keys [project-name build-num vcs-type vcs-url vcs-revision actions action-results group num-nodes lb-name continue]
                :or {failed false}}]
  (Build. project-name build-num vcs-type vcs-url vcs-revision actions (or action-results []) group num-nodes lb-name true))

(defrecord BuildContext [build
                         action
                         node])

(defn run-build [build]
  (let [node (atom nil)
        update-node (fn update-node [build]
                      (when (not (seq @node))
                        (swap! node (fn [_]
                                      (-> build :group (node-info) (first))))))]
    (when (= :deploy (:type build))
      (throw-if-not (:vcs-revision) "version-control revision is required for deploys"))
    (with-pwd "" ;; bind here, so actions can set! it
      (fold build [act (-> build :actions)]
        (update-node build)
        (if (-> build :continue)
          (let [context {:build build
                         :action act
                         :node @node}
                _ (println "calling" (-> act :name))
                action-result (-> act :act-fn (.invoke context))]
            (println "action-result for" (-> act :name) "is:" action-result)
            (validate-action-result! action-result)
            (-> build
                (update-in [:action-results] conj action-result)
                (update-in [:continue] (fn [_] (continue? action-result)))))
          build)))))

(defn extend-group-with-revision
  "update the build with a new group that extends the existing group at the :group key."
  [build]
  (-> build
      (update-in [:group] merge
                 {:group-name (keyword (.toLowerCase (format "%s-%s" (-> build :project-name) (-> build :vcs-revision))))})))

(defn successful? [build-result]
  (-> build-result :continue))