(ns frontend.send
  (:require [cljs.core.async :refer [chan]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [frontend.api :as api]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next :as om-next]
            [om.util :as om-util]
            [clojure.string :as string])
  (:require-macros [cljs.core.async.macros :as am :refer [go-loop]]))

;; These spec should find a better place to live, but data generation in `send`
;; is all they're used for currently, they can live here for now.
(s/def :organization/vcs-type #{"github" "bitbucket"})
(s/def :organization/name string?)
(s/def :organization/entity (s/keys :req [:organization/vcs-type :organization/name]))

(s/def :project/name string?)
(s/def :project/organization :organization/entity)
(s/def :project/entity (s/keys :req [:project/name :project/organization]))

(s/def :workflow/id uuid?)
(s/def :workflow/name string?)
(s/def :workflow/runs (s/every :run/entity))
(s/def :workflow/project :project/entity)
(s/def :workflow/entity (s/keys :req [:workflow/id
                                      :workflow/name
                                      :workflow/runs
                                      :workflow/project]))

(s/def :run/id uuid?)
(s/def :run/status #{:run-status/queued
                     :run-status/running
                     :run-status/succeeded
                     :run-status/failed
                     :run-status/canceled})
(s/def :run/started-at inst?)
(s/def :run/stopped-at inst?)
(s/def :run/branch string?)
(s/def :run/commit-sha (s/with-gen (s/and string?
                                          #(= 40 (count %))
                                          #_(partial re-find #"^[0-9A-Fa-f]*$"))
                         ;; TODO Make these real shas.
                         #(gen/fmap string/join (gen/vector gen/char-alphanumeric 40))))
(s/def :run/entity (s/keys :req [:run/id
                                 :run/status
                                 :run/started-at
                                 :run/stopped-at
                                 :run/branch
                                 :run/commit-sha]))

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
             (= :workflow/by-org-project-and-name (first (om-util/join-key expr))))
        ;; Generate fake data for now.
        (cb (rewrite {(om-util/join-key expr)
                      (let [ident-vals (second (om-util/join-key expr))]
                        (gen/generate
                         (s/gen :workflow/entity
                                {[:workflow/name] #(gen/return (:workflow/name ident-vals))
                                 [:workflow/project :project/name] #(gen/return (:project/name ident-vals))
                                 [:workflow/project :project/organization :organization/name] #(gen/return (:organization/name ident-vals))
                                 [:workflow/project :project/organization :organization/vcs-type] #(gen/return (:organization/vcs-type ident-vals))})))})
            ui-query)

        :else (throw (str "No clause found for " (pr-str expr)))))))


(defn send [remotes cb]
  (doseq [remote-entry remotes]
    (send* remote-entry cb)))
