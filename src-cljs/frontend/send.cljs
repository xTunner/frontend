(ns frontend.send
  (:require [cljs-time.coerce :as time-coerce]
            [cljs-time.core :as time]
            [cljs.core.async :refer [chan]]
            [clojure.set :refer [rename-keys]]
            [frontend.api :as api]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next.impl.parser :as om-parser])
  (:require-macros [cljs.core.async.macros :as am :refer [go-loop]]))

(defn- callback-api-chan
  "Returns a channel which can be used with the API functions. Calls cb with the
  response data when the API call succeeds. Ignores failures.

  This is a temporary shim to reuse the old API functions in the Om Next send."
  [cb]
  (let [ch (chan)]
    (go-loop []
      (let [[_ state data] (<! ch)]
        (when (= state :success)
          (cb (:resp data)))
        (when-not (= state :finished)
          (recur))))
    ch))

(defn- job-run-status [job-status-str]
  (case job-status-str
    ("fixed" "success") :job-run-status/succeeded
    "failed" :job-run-status/failed
    "canceled" :job-run-status/canceled
    "not_run" :job-run-status/not-run
    "running" :job-run-status/running
    ("waiting" "queued" "not_running") :job-run-status/waiting))

(defn adapt-to-job [job-response]
  (update job-response :job/status job-run-status))

(defn- compute-run-stop-time [jobs]
  (some->> jobs
           (keep (comp time-coerce/from-date :job/stopped-at))
           not-empty
           time/latest
           time-coerce/to-date))

(defn- run-status [run-status-str]
  (case run-status-str
    "success" :run-status/succeeded
    "failed" :run-status/failed
    "running" :run-status/running
    "not_run" :run-status/not-run
    "canceled" :run-status/canceled))

(defn adapt-to-run
  [response]
  (let [status (-> response :workflow/status run-status)
        run-id (:workflow/id response)
        jobs (mapv (fn [job-response]
                     (-> job-response
                         adapt-to-job
                         (assoc-in [:job/run :run/id] run-id)))
                   (:workflow/jobs response))
        {:keys [build/vcs-type build/org build/repo]} (get-in response
                                                              [:workflow/jobs
                                                               0
                                                               :job/build])]

    {:run/id run-id
     :run/name (:workflow/name response)
     :run/status status
     :run/started-at (:workflow/created-at response)
     :run/stopped-at (if (#{:run-status/running :run-status/not-run} status)
                       nil
                       (compute-run-stop-time jobs))
     :run/jobs jobs
     :run/trigger-info (:workflow/trigger-info response)
     :run/project {:project/name repo
                   :project/organization {:organization/vcs-type vcs-type
                                          :organization/name org}}}))

(defn- de-alias-expression [expr cb]
  (let [ast (om-parser/expr->ast expr)]
    (if-let [aliased-from (get-in ast [:params :<])]
      [(om-parser/ast->expr (-> ast
                                (update :params dissoc :<)
                                (assoc :key aliased-from)))
       ;; Wrap the callback to rename the key in the result back to the alias.
       #(cb (rename-keys %1 {aliased-from (:key ast)}) %2)]
      [expr cb])))

(defmulti send* key)

;; This implementation is merely a prototype, which does some rudimentary
;; pattern-matching against a few expected cases to decide which APIs to hit. A
;; more rigorous implementation will come later.
(defmethod send* :remote [[_ query] cb]
  (doseq [expr query]
    (let [[expr cb] (de-alias-expression expr cb)
          ast (om-parser/expr->ast expr)]
      (cond
        (= {:app/current-user [{:user/organizations [:organization/name :organization/vcs-type :organization/avatar-url]}]}
           expr)
        (let [ch (callback-api-chan
                  #(let [orgs (for [api-org %]
                                {:organization/name (:login api-org)
                                 :organization/vcs-type (:vcs_type api-org)
                                 :organization/avatar-url (:avatar_url api-org)})]
                     (cb {:app/current-user {:user/organizations (vec orgs)}} query)))]
          (api/get-orgs ch :include-user? true))

        ;; :route/projects
        (and (= :circleci/organization (:key ast))
             (= '[:organization/vcs-type
                  :organization/name
                  {:organization/projects [:project/follower-count
                                           :project/vcs-url
                                           :project/name
                                           :project/parallelism
                                           :project/oss?]}
                  {:organization/plan [*]}]
                (:query ast)))
        (let [{:keys [organization/vcs-type organization/name]} (:params ast)]
          (api/get-org-settings
           name vcs-type
           (callback-api-chan
            #(let [projects (for [p (:projects %)]
                              {:project/vcs-url (:vcs_url p)
                               :project/name (vcs-url/repo-name (:vcs_url p))
                               :project/parallelism (:parallel p)
                               ;; Sometimes the backend returns a map of feature_flags,
                               ;; and sometimes it returns :oss directly on the project.
                               :project/oss? (or (:oss p)
                                                 (get-in p [:feature_flags :oss]))
                               :project/follower-count (count (:followers p))})]
               (cb {:circleci/organization {:organization/name name
                                            :organization/vcs-type vcs-type
                                            :organization/projects (vec projects)}} query))))
          (api/get-org-plan
           name vcs-type
           (callback-api-chan
            #(cb {:circleci/organization {:organization/name name
                                          :organization/vcs-type vcs-type
                                          :organization/plan %}}
                 query))))

        ;; Also :route/projects (but a separate expression for analytics, which
        ;; doesn't actually need to hit the server)
        (and (= :circleci/organization (:key ast))
             (= '[:organization/name] (:query ast)))
        (let [{:keys [organization/vcs-type organization/name]} (:params ast)]
          (cb {:circleci/organization {:organization/name name}} query))


        ;; :route/workflow
        (let [{:keys [key children]} ast]
          (and (= :circleci/organization key)
               (= 1 (count children))
               (= :organization/project (:key (first children)))
               (= [:project/name
                   {:project/organization [:organization/vcs-type
                                           :organization/name]}
                   {:project/workflow-runs [:run/id
                                            :run/name
                                            :run/status
                                            :run/started-at
                                            :run/stopped-at
                                            {:run/trigger-info [:trigger-info/vcs-revision
                                                                :trigger-info/subject
                                                                :trigger-info/body
                                                                :trigger-info/branch
                                                                {:trigger-info/pull-requests [:pull-request/url]}]}
                                            {:run/project [:project/name
                                                           {:project/organization [:organization/name
                                                                                   :organization/vcs-type]}]}]}]
                  (:query (first children)))))
        (let [project-ast (first (:children ast))
              vcs-type (:organization/vcs-type (:params ast))
              org-name (:organization/name (:params ast))
              project-name (:project/name (:params project-ast))]
          (api/get-project-workflows
           (callback-api-chan
            (fn [response]
              (cb {:circleci/organization
                   {:organization/project
                    {:project/name project-name
                     :project/organization {:organization/vcs-type vcs-type
                                            :organization/name name}
                     :project/workflow-runs (mapv adapt-to-run response)}}}
                  query)))
           (vcs-url/vcs-url vcs-type org-name project-name)))

        ;; :route/job
        (let [{:keys [key children]} ast]
          (and (= :circleci/run key)
               (= 1 (count children))
               (= :run/job (:key (first children)))
               (= [:job/build :job/name]
                  (:query (first children)))))
        (let [job-ast (first (:children ast))
              job-name (:job/name (:params job-ast))]
          (api/get-workflow-status
           (callback-api-chan
            (fn [response]
              (cb {:circleci/run {:run/job (->> response
                                                adapt-to-run
                                                :run/jobs
                                                (filter #(= job-name (:job/name %)))
                                                first)}}
                  query)))
           (:run/id (:params ast))))
        ;; Also :route/workflow (but a separate expression for breadcrumbs, which
        ;; doesn't actually need to hit the server)
        (and (= :circleci/organization (:key ast))
             (= '[:organization/vcs-type :organization/name] (:query ast)))
        (let [{:keys [organization/vcs-type organization/name]} (:params ast)]
          (cb {:circleci/organization {:organization/vcs-type vcs-type
                                       :organization/name name}}
              query))

        ;; Also :route/workflow (but *another( expression for breadcrumbs, which
        ;; doesn't actually need to hit the server)
        (let [{:keys [key children]} ast]
          (and (= :circleci/organization key)
               (= 1 (count children))
               (= :organization/project (:key (first children)))
               (= '[:project/name] (:query (first children)))))
        (let [{:keys [organization/vcs-type organization/name]} (:params ast)]
          (cb {:circleci/organization {:organization/vcs-type vcs-type
                                       :organization/name name}}
              query))


        ;; :route/run
        (let [{:keys [key query]} ast]
          (and (= :circleci/run key)
               (= [:run/id
                   :run/name
                   :run/status
                   :run/started-at
                   :run/stopped-at
                   {:run/trigger-info [:trigger-info/vcs-revision
                                       :trigger-info/subject
                                       :trigger-info/body
                                       :trigger-info/branch
                                       {:trigger-info/pull-requests [:pull-request/url]}]}
                   {:run/project [:project/name
                                  {:project/organization [:organization/name
                                                          :organization/vcs-type]}]}]
                  query)))
        (api/get-workflow-status
         (callback-api-chan
          (fn [response]
            (cb {:circleci/run (-> (adapt-to-run response)
                                   (dissoc :run/jobs))}
                query)))
         (:run/id (:params ast)))
        (let [{:keys [key query]} ast]
          (and (= :circleci/run key)
               (= '[({:jobs-for-jobs [:job/id
                                      :job/status
                                      :job/started-at
                                      :job/stopped-at
                                      :job/name
                                      {:job/run [:run/id]}]}
                     {:< :run/jobs})
                    ({:jobs-for-first [:job/id
                                       :job/build
                                       :job/name]}
                     {:< :run/jobs})]
                  query)))
        (api/get-workflow-status
         (callback-api-chan
          (fn [response]
            (let [run (adapt-to-run response)
                  run-with-aliases (-> run
                                       (dissoc :run/jobs)
                                       (assoc :jobs-for-jobs (mapv #(select-keys % [:job/id
                                                                                    :job/status
                                                                                    :job/started-at
                                                                                    :job/stopped-at
                                                                                    :job/name
                                                                                    :job/run])
                                                                   (:run/jobs run))
                                              :jobs-for-first (mapv #(select-keys % [:job/id
                                                                                     :job/build
                                                                                     :job/name])
                                                                    (:run/jobs run))))]
              (cb {:circleci/run run-with-aliases} query))))
         (:run/id (:params ast)))

        :else (throw (str "No clause found for " (pr-str expr)))))))

(defn send [remotes cb]
  (doseq [remote-entry remotes]
    (send* remote-entry cb)))
