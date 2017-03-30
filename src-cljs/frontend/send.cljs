(ns frontend.send
  (:require [cljs-time.coerce :as time-coerce]
            [cljs-time.core :as time]
            [cljs.core.async :refer [chan]]
            [clojure.spec :as s :include-macros true]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [clojure.test.check.generators :as gen]
            [frontend.api :as api]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next :as om-next]
            [om.util :as om-util])
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

(defn adapt-to-job [job-response]
  (let [status->job-run-status {"success" :job-run-status/succeeded
                                "failed" :job-run-status/failed
                                "canceled" :job-run-status/canceled
                                "running" :job-run-status/running
                                "waiting" :job-run-status/waiting}]
    {:job-run/id (:job/id job-response)
     :job-run/status (status->job-run-status (:job/status job-response))
     :job-run/started-at (js/Date.) ;; FIXME
     :job-run/stopped-at (js/Date.) ;; FIXME
     :job-run/job {:job/id (:job/id job-response)
                   :job/name (:job/name job-response)}}))

(defn adapt-to-run
  [response]
  {:run/id (:workflow/id response)
   :run/name (:workflow/name response)
   :run/status :run-status/succeeded
   :run/started-at (:workflow/created-at response)
   :run/stopped-at nil  ;; FIXME
   :run/branch-name (get-in response [:workflow/trigger-resource :data :branch])
   :run/commit-sha (get-in response [:workflow/trigger-resource :data :vcs_revision])
   :run/job-runs (mapv adapt-to-job (:workflow/jobs response))})

(defn adapt-to-workflow
  [response]
  {:workflow/id (:workflow/id response)
   :workflow/name (:workflow/name response)
   :workflow/project {:project/name (get-in response [:workflow/trigger-resource :data :reponame])
                      :project/organization {:organization/name (get-in response [:workflow/trigger-resource :data :username])
                                             :organization/vcs-type "github"}} ;; FIXME
   :workflow/runs [(adapt-to-run response)]})


(defmulti send* key)

;; This implementation is merely a prototype, which does some rudimentary
;; pattern-matching against a few expected cases to decide which APIs to hit. A
;; more rigorous implementation will come later.
(defmethod send* :remote
  [[_ ui-query] cb]
  (let [{:keys [query rewrite]} (om-next/process-roots ui-query)]
    (doseq [expr query]
      (cond
       (= {:app/current-user [{:user/organizations [:organization/name :organization/vcs-type :organization/avatar-url]}]}
          expr)
       (let [ch (callback-api-chan
                 #(let [orgs (for [api-org %]
                               {:organization/name (:login api-org)
                                :organization/vcs-type (:vcs_type api-org)
                                :organization/avatar-url (:avatar_url api-org)})]
                    (cb (rewrite {:app/current-user {:user/organizations (vec orgs)}}) ui-query)))]
         (api/get-orgs ch :include-user? true))

       (and (om-util/ident? (om-util/join-key expr))
            (= :organization/by-vcs-type-and-name (first (om-util/join-key expr)))
            (= '[:organization/vcs-type
                 :organization/name
                 {:organization/projects [:project/vcs-url
                                          :project/name
                                          :project/parallelism
                                          :project/oss?
                                          {:project/followers []}]}
                 {:organization/plan [*]}]
               (om-util/join-value expr)))
       (let [{:keys [organization/vcs-type organization/name]} (second (om-util/join-key expr))]
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
                              :project/followers (vec (for [u (:followers p)]
                                                        {}))})
                  org {:organization/name name
                       :organization/vcs-type vcs-type
                       :organization/projects (vec projects)}]
              (cb (rewrite {(om-util/join-key expr) org}) ui-query))))
         (api/get-org-plan
          name vcs-type
          (callback-api-chan
           #(cb (rewrite {(om-util/join-key expr) {:organization/name name
                                                   :organization/vcs-type vcs-type
                                                   :organization/plan %}})
                ui-query))))

       (and (om-util/ident? (om-util/join-key expr))
            (= :organization/by-vcs-type-and-name (first (om-util/join-key expr)))
            (= '[:organization/vcs-type
                 :organization/name]
               (om-util/join-value expr)))
       (let [{:keys [organization/vcs-type organization/name]} (second (om-util/join-key expr))]
         (let [org {:organization/name name
                    :organization/vcs-type vcs-type}]
           (cb (rewrite {(om-util/join-key expr) org}) ui-query)))

       (and (om-util/ident? (om-util/join-key expr))
            (= :project/by-org-and-name (first (om-util/join-key expr)))
            (= '[:project/name]
               (om-util/join-value expr)))
       (let [{:keys [project/name]} (second (om-util/join-key expr))]
         (let [project {:project/name name}]
           (cb (rewrite {(om-util/join-key expr) project}) ui-query)))

       (and (om-util/ident? (om-util/join-key expr))
            (= :workflow/by-org-project-and-name (first (om-util/join-key expr))))
       ;; Generate fake data for now.
       (api/get-workflow-status
        (callback-api-chan
         (fn [response]
           (cb (rewrite {(om-util/join-key expr) (adapt-to-workflow response)})
               ui-query))))

       (and (om-util/ident? (om-util/join-key expr))
            (= :run/by-id (first (om-util/join-key expr))))
       (api/get-workflow-status
        (callback-api-chan
         (fn [response]
           (cb (rewrite {(om-util/join-key expr) (adapt-to-run response)})
               ui-query))))

       :else (throw (str "No clause found for " (pr-str expr)))))))

(defn send [remotes cb]
  (doseq [remote-entry remotes]
    (send* remote-entry cb)))
