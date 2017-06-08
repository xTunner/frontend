(ns frontend.send
  (:require [bodhi.aliasing :as aliasing]
            [bodhi.core :as bodhi]
            [cljs-time.coerce :as time-coerce]
            [cljs-time.core :as time]
            [cljs.core.async :refer [<! chan]]
            [clojure.set :refer [rename-keys]]
            [frontend.api :as api]
            [frontend.send.resolve :refer [resolve]]
            [frontend.utils.expr-ast :as expr-ast]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next :as om-next]
            [om.next.impl.parser :as om-parser]
            [promesa.core :as p :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go-loop]]))

(def ^:private ms-until-retried-run-retrieved 3000)

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

(defn- api-promise
  "Takes an API function which expects a channel as its first arg, followed by
  the API functions other args. Returns a promise of the response the API
  function puts on the channel.

  This is a temporary shim to reuse the old API functions in the Om Next send."
  [api-f & args]
  (p/promise
   (fn [resolve reject]
     (let [ch (chan)]
       (go-loop []
         (let [[_ state data] (<! ch)]
           (when (= state :success)
             (resolve (:resp data)))
           (when-not (= state :finished)
             (recur))))
       (apply api-f ch args)))))

(defn- job-run-status [job-status-str]
  (case job-status-str
    ("fixed" "success") :job-run-status/succeeded
    "failed" :job-run-status/failed
    "timedout" :job-run-status/timed-out
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

(def ^:private ignore-response-callback (fn [_response] nil))

(defn- request-run-retry-from-api-service
  [params]
  (api/request-retry-run (callback-api-chan ignore-response-callback) params))

(defn- request-run-cancel-from-api-service
  [id]
  (api/request-cancel-run (callback-api-chan ignore-response-callback) id))

(defn- send-retry-run
  [{:keys [params]} merge-fn query]
  (request-run-retry-from-api-service params))

(defn- send-cancel-run
  [{:keys [params]} merge-fn query]
  (let [{:keys [run/id]} params]
    (request-run-cancel-from-api-service id)))

(defn- retry-run-expression?
  [expression]
  (= 'run/retry (first expression)))

(defn- cancel-run-expression?
  [expression]
  (= 'run/cancel (first expression)))

(defn- branch-crumb-ast?
  "returns true if the ast is for an expression rendering the branch
  breadcrumb"
  [expr-ast]
  (boolean
   (and (= :circleci/organization
           (:key expr-ast))
        (expr-ast/has-children? expr-ast
                                #{:organization/project})
        (-> expr-ast
            (expr-ast/get :organization/project)
            (expr-ast/has-children? #{:project/branch}))
        (-> expr-ast
            (expr-ast/get-in [:organization/project :project/branch])
            (expr-ast/has-children? #{:branch/name})))))

(defn- branch-runs-ast?
  "returns true if the ast is for an expression rendering the branch
  workflow-runs page"
  [expr-ast]
  (boolean
   (and (= :circleci/organization (:key expr-ast))
        (expr-ast/has-children? expr-ast
                                #{:organization/project})
        (-> expr-ast
            (expr-ast/get :organization/project)
            (expr-ast/has-children? #{:project/branch}))
        (-> expr-ast
            (expr-ast/get-in [:organization/project :project/branch])
            (expr-ast/has-children? #{:branch/name :routed-page :branch/project}))
        (-> expr-ast
            (expr-ast/get-in [:organization/project
                              :project/branch
                              :branch/project])
            (expr-ast/has-children? #{:project/organization :project/name}))
        (-> expr-ast
            (expr-ast/get-in [:organization/project
                              :project/branch
                              :branch/project
                              :project/organization])
            (expr-ast/has-children? #{:organization/name})))))

(defn- get-branch-runs [cb expr-ast query]
  (let [vcs-type (-> expr-ast :params :organization/vcs-type)
        org-name (-> expr-ast :params :organization/name)
        repo-name (-> expr-ast
                      (expr-ast/get-in [:organization/project])
                      :params
                      :project/name)
        branch-name (-> expr-ast
                        (expr-ast/get-in [:organization/project :project/branch])
                        :params
                        :branch/name)
        {:keys [connection/offset connection/limit]}
        (:params (expr-ast/get-in expr-ast [:organization/project :project/branch :routed-page]))]
    (api/get-branch-workflows (callback-api-chan
                               (fn [response]
                                 (let [novelty {:circleci/organization
                                                {:organization/project
                                                 {:project/branch
                                                  {:routed-page
                                                   {:connection/total-count (:total-count response)
                                                    :connection/edges (->> (:results response)
                                                                           (map adapt-to-run)
                                                                           (mapv #(hash-map :edge/node %)))}}}}}]
                                   (cb novelty query))))
                              vcs-type
                              org-name
                              repo-name
                              branch-name
                              {:offset offset :limit limit})))

(defn- project-runs-ast? [expr-ast]
  (and (= :circleci/organization
          (:key expr-ast))
       (expr-ast/has-children? expr-ast #{:organization/project})
       (or
        (-> expr-ast
            (expr-ast/get :organization/project)
            (expr-ast/has-children? #{:project/name :routed-page :project/organization})))
       (-> expr-ast
           (expr-ast/get-in [:organization/project :project/organization])
           (expr-ast/has-children? #{:organization/vcs-type :organization/name}))))

(defn- project-crumb-ast? [expr-ast]
  (and (= :circleci/organization
          (:key expr-ast))
       (expr-ast/has-children? expr-ast #{:organization/project})
       (-> expr-ast
           (expr-ast/get :organization/project)
           (expr-ast/has-children? #{:project/name}))))

(defn- get-project-name [cb expr-ast query]
  (let [project-name (-> expr-ast
                         (expr-ast/get :organization/project)
                         :params
                         :project/name)
        novelty {:circleci/organization
                 {:organization/project
                  {:project/name project-name}}}]
    (cb novelty query)))

(defn- run-page-crumbs-ast? [expr-ast]
  (and (= :circleci/run (:key expr-ast))
       (expr-ast/has-children? expr-ast #{:run/id :run/project :run/trigger-info})
       (-> expr-ast
           (expr-ast/get :run/project)
           (expr-ast/has-children? #{:project/name :project/organization}))
       (-> expr-ast
           (expr-ast/get-in [:run/project :project/organization])
           (expr-ast/has-children? #{:organization/vcs-type :organization/name}))
       (-> expr-ast
           (expr-ast/get :run/trigger-info)
           (expr-ast/has-children? #{:trigger-info/branch}))))

;; The parser used by the resolvers when they return deeply nested data.
(def parser
  (om-next/parser {:read (bodhi/read-fn
                          (-> bodhi/basic-read
                              aliasing/read))}))

(def resolvers
  {:circleci/organization
   (fn [env ast]
     (resolve (assoc env
                     :organization/vcs-type (:organization/vcs-type (:params ast))
                     :organization/name (:organization/name (:params ast)))
              ast
              (chan)))

   :organization/project
   (fn [env ast]
     (resolve (assoc env :project/name (:project/name (:params ast)))
              ast
              (chan)))

   :project/branch
   (fn [env ast]
     (resolve (assoc env :branch/name (:branch/name (:params ast)))
              ast
              (chan)))

   :branch/name
   (fn [env ast]
     (:branch/name env))

   :branch/project
   (fn [env ast]
     (resolve (dissoc env :branch/name)
              ast
              (chan)))

   :project/name
   (fn [env ast]
     (:project/name env))

   :project/organization
   (fn [env ast]
     (resolve (dissoc env :project/name)
              ast
              (chan)))

   :organization/name
   (fn [env ast]
     (:organization/name env))

   :branch/workflow-runs
   (fn [env ast]
     (-> (api-promise
          api/get-branch-workflows
          (:organization/vcs-type env)
          (:organization/name env)
          (:project/name env)
          (:branch/name env)
          {:offset (:connection/offset (:params ast))
           :limit (:connection/limit (:params ast))})

         (p/then
          (fn [response]
            (let [adapted {:connection/total-count (:total-count response)
                           :connection/edges (->> (:results response)
                                                  (map adapt-to-run)
                                                  (mapv #(hash-map :edge/node %)))}]
              ;; Run the adapted structure through a parser to match it to the
              ;; rest of the query.
              (parser {:state (atom adapted)} (mapv om-next/ast->query (:children ast))))))))

   :project/workflow-runs
   (fn [env ast]
     (-> (api-promise
          api/get-project-workflows
          (vcs-url/vcs-url (:organization/vcs-type env)
                           (:organization/name env)
                           (:project/name env))
          {:offset (:connection/offset (:params ast))
           :limit (:connection/limit (:params ast))})

         (p/then
          (fn [response]
            (let [adapted {:connection/total-count (:total-count response)
                           :connection/edges (->> (:results response)
                                                  (map adapt-to-run)
                                                  (mapv #(hash-map :edge/node %)))}]
              ;; Run the adapted structure through a parser to match it to the
              ;; rest of the query.
              (parser {:state (atom adapted)} (mapv om-next/ast->query (:children ast))))))))

   :app/current-user
   (fn [env ast]
     (-> (api-promise api/get-orgs :include-user? true)
         (p/then
          (fn [response]
            (let [adapted {:user/organizations
                           (mapv #(hash-map :organization/name (:login %)
                                            :organization/vcs-type (:vcs_type %)
                                            :organization/avatar-url (:avatar_url %))
                                 response)}]
              ;; Run the adapted structure through a parser to match it to the
              ;; rest of the query.
              (parser {:state (atom adapted)} (mapv om-next/ast->query (:children ast))))))))

   :organization/vcs-type
   (fn [env ast]
     (:organization/vcs-type env))

   #{:organization/avatar-url :organization/current-user-is-admin?}
   (fn [{:keys [organization/vcs-type organization/name] :as env} ast]
     (-> (api-promise api/get-orgs :include-user? true)
         (p/then
          (fn [response]
            (let [selected-org (first (filter (fn [{:keys [vcs_type login]}]
                                                (and (= vcs_type vcs-type)
                                                     (= login name)))
                                              response))]
              {:organization/avatar-url (:avatar_url selected-org)
               :organization/current-user-is-admin? (:admin selected-org)})))))

   :organization/projects
   (fn [{:keys [organization/vcs-type organization/name] :as env} ast]
     ;; Unlike most of the api functions, api/get-org-settings takes the channel
     ;; as its last argument, so we use `partial` to make a function which takes
     ;; it first, as `api-promise` requires.
     (-> (api-promise (partial api/get-org-settings name vcs-type))
         (p/then
          (fn [response]
            (let [projects (for [p (:projects response)]
                             {:project/vcs-url (:vcs_url p)
                              :project/name (vcs-url/repo-name (:vcs_url p))
                              :project/parallelism (:parallel p)
                              ;; Sometimes the backend returns a map of feature_flags,
                              ;; and sometimes it returns :oss directly on the project.
                              :project/oss? (or (:oss p)
                                                (get-in p [:feature_flags :oss]))
                              :project/follower-count (count (:followers p))})]
              (mapv #(parser {:state (atom %)} (mapv om-next/ast->query (:children ast))) projects))))))

   :organization/plan
   (fn [{:keys [organization/vcs-type organization/name] :as env} ast]
     ;; Unlike most of the api functions, api/get-org-settings takes the channel
     ;; as its last argument, so we use `partial` to make a function which takes
     ;; it first, as `api-promise` requires.
     ;;
     ;; Also: note that we do no processing/massaging of this data. The plan
     ;; data is currently used in it's old-api form rather than using new
     ;; namespaced, universal keys. This is debt.
     (-> (api-promise (partial api/get-org-plan name vcs-type))))

   :circleci/run
   (fn [env ast]
     (resolve (assoc env :run/id (:run/id (:params ast)))
              ast
              (chan)))

   :run/job
   (fn [env ast]
     (resolve (assoc env :job/name (:job/name (:params ast)))
              ast
              (chan)))

   :job/name
   (fn [env ast]
     (:job/name env))

   :job/build
   (fn [{:keys [run/id job/name] :as env} ast]
     ;; Unlike most of the api functions, api/get-org-settings takes the channel
     ;; as its last argument, so we use `partial` to make a function which takes
     ;; it first, as `api-promise` requires.
     (-> (api-promise api/get-workflow-status id)
         (p/then
          (fn [response]
            (let [adapted (->> response
                               adapt-to-run
                               :run/jobs
                               (filter #(= name (:job/name %)))
                               first)]
              (parser {:state (atom adapted)} (mapv om-next/ast->query (:children ast))))))))

   :run/id
   (fn [env ast]
     (:run/id env))

   #{:run/name
     :run/status
     :run/started-at
     :run/stopped-at}
   (fn [{:keys [run/id] :as env} ast]
     (-> (api-promise api/get-workflow-status id)
         (p/then adapt-to-run)))

   ;; The current implementation of `resolve` makes deeply-nested
   ;; responses (using a parser like this) and matching multiple keys (using a
   ;; set as a key in the resolvers map) not really work together well. Instead
   ;; we have to repeat ourselves for each of these non-scalar parts of a run.
   ;; This is something `resolve` should improve.
   :run/jobs
   (fn [{:keys [run/id] :as env} ast]
     (-> (api-promise api/get-workflow-status id)
         (p/then
          (fn [response]
            (let [jobs (:run/jobs (adapt-to-run response))]
              (mapv #(parser {:state (atom %)} (mapv om-next/ast->query (:children ast))) jobs))))))

   :run/trigger-info
   (fn [{:keys [run/id] :as env} ast]
     (-> (api-promise api/get-workflow-status id)
         (p/then
          (fn [response]
            (let [adapted (:run/trigger-info (adapt-to-run response))]
              (parser {:state (atom adapted)} (mapv om-next/ast->query (:children ast))))))))

   :run/project
   (fn [{:keys [run/id] :as env} ast]
     (-> (api-promise api/get-workflow-status id)
         (p/then
          (fn [response]
            (let [adapted (:run/project (adapt-to-run response))]
              (parser {:state (atom adapted)} (mapv om-next/ast->query (:children ast))))))))})

(defmulti send* key)

;; This implementation is merely a prototype, which does some rudimentary
;; pattern-matching against a few expected cases to decide which APIs to hit. A
;; more rigorous implementation will come later.
(defmethod send* :remote [[_ query] cb]
  (if-let [send-fn (cond (retry-run-expression? (first query))
                      send-retry-run
                      (cancel-run-expression? (first query))
                      send-cancel-run)]
    ;; If we're rerunning, give the rerun mutation a chance to take effect on
    ;; the backend before continuing with any reads.
    (do
      (send-fn (om-parser/expr->ast (first query)) cb query)
      (js/setTimeout #(send* [:remote (rest query)] cb)
                     ms-until-retried-run-retrieved))
    (doseq [expr query]
      (let [ch (resolve {:resolvers resolvers} [expr] (chan))]
        (go-loop []
          (when-let [novelty (<! ch)]
            (cb novelty query)
            (recur)))))))

(defn send [remotes cb]
  (doseq [remote-entry remotes]
    (send* remote-entry cb)))
