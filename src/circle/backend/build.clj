(ns circle.backend.build)

(defrecord Build [project-name ;; string
                  build-num ;; int
                  vcs-type
                  vcs-url
                  vcs-revision ;; if present, the commit that caused the build to be run, or nil
                  aws-credentials ;; map containing :user and :password
                  r53-zone-id   ;; zone-id of the domain we're managing. Required for DNS updates.
                  notify-email ;; an email address to notify when build is done
                  actions ;; a seq of actions
                  action-results
                  group ;; the pallet group spec to use for the build
                  num-nodes ;; number of nodes to start/use
                  lb-name   ;; name of the load-balancer to use
                  continue  ;; if true, continue running the build. Failed actions will set this to false
                  ])

;; (defn build [& {:keys [project-name build-num vcs-type vcs-url vcs-revision aws-credentials r53-zone-id notify-email actions action-results group num-nodes lb-name continue]
;;                 :or {failed false}}]
;;   (Build. project-name build-num vcs-type vcs-url vcs-revision aws-credentials r53-zone-id notify-email actions (or action-results []) group num-nodes lb-name true))

(def build hash-map)

(defrecord BuildContext [build
                         action
                         node])

(defn extend-group-with-revision
  "update the build with a new group that extends the existing group at the :group key."
  [build]
  (-> build
      (update-in [:group] merge
                 {:group-name (keyword (.toLowerCase (format "%s-%s" (-> build :project-name) (-> build :vcs-revision))))})))

(defn successful? [build-result]
  (-> build-result :continue))