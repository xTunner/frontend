(ns frontend.send
  (:require [cljs.core.async :refer [chan]]
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
        (api/get-orgs
         (callback-api-chan
          #(let [orgs (for [api-org %]
                        {:organization/name (:login api-org)
                         :organization/vcs-type (:vcs_type api-org)
                         :organization/avatar-url (:avatar_url api-org)})]
             (cb (rewrite {:app/current-user {:user/organizations (vec orgs)}}) ui-query))))

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

        :else (throw (str "No clause found for " (pr-str expr)))))))


(defn send [remotes cb]
  (doseq [remote-entry remotes]
    (send* remote-entry cb)))
