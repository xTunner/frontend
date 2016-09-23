(ns frontend.utils.state
  (:require [frontend.state :as state]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.seq :refer [find-index]]
            [frontend.models.plan :as plan]
            [clojure.string :as string])
  (:require-macros [frontend.utils :refer [inspect]]))

(defn set-dashboard-crumbs [state {:keys [org repo branch vcs_type]}]
  (assoc-in state state/crumbs-path
            (vec (concat
                   [{:type :dashboard}]
                   (when org [{:type :org
                               :username org
                               :vcs_type vcs_type}])
                   (when repo [{:type :project
                                :username org :project repo
                                :vcs_type vcs_type}])
                   (when branch [{:type :project-branch
                                  :username org :project repo :branch branch
                                  :vcs_type vcs_type}])))))

(defn reset-current-build [state]
  (assoc state :current-build-data {:build nil
                                    :usage-queue-data {:builds nil
                                                       :show-usage-queue false}
                                    :artifact-data {:artifacts nil
                                                    :show-artifacts false}
                                    :container-data {:current-container-id 0
                                                     :current-filter :all
                                                     :paging-offset 0
                                                     :containers nil}}))

(defn reset-current-project [state]
  (assoc state :current-project-data {:project nil
                                      :plan nil
                                      :settings {}
                                      :tokens nil
                                      :checkout-keys nil
                                      :envvars nil}))

(defn reset-current-org [state]
  (assoc state :current-org-data {:plan nil
                                  :projects nil
                                  :users nil
                                  :name nil}))

(defn stale-current-project? [state project-name]
  (and (get-in state state/project-path)
       (not= project-name (vcs-url/project-name (get-in state (conj state/project-path :vcs_url))))))

(defn stale-current-org? [state org-name]
  (and (get-in state state/org-name-path)
       (not= org-name (get-in state state/org-name-path))))

(defn stale-current-build?
  [state project-name build-num]
  (or (stale-current-project? state project-name)
      (if-let [current-build-num (get-in state
                                         [:current-build-data :build :build_num])]
        (not= build-num current-build-num)
        true)))

(defn find-repo-index
  "Path for a given repo. Login is the username, name is the repo name."
  [repos login repo-name]
  (when repos
    (find-index #(and (= repo-name (:name %))
                      (= login (:username %)))
                repos)))

(defn clear-page-state [state]
  (-> state
      (assoc :crumbs nil)
      (assoc-in state/inputs-path nil)
      (assoc-in state/error-message-path nil)
      (assoc-in state/general-message-path nil)
      (assoc-in state/page-scopes-path nil)))

(defn merge-inputs [defaults inputs keys]
  (merge (select-keys defaults keys)
         (select-keys inputs keys)))

(defn reset-dismissed-osx-usage-level [state]
  (let [plan (get-in state state/project-plan-path)]
    (if (< (plan/current-months-osx-usage-% plan) plan/first-warning-threshold)
      (assoc-in state state/dismissed-osx-usage-level plan/first-warning-threshold)
      state)))

(defn build-parts
  ([build]
   (let [[username project] (string/split (vcs-url/project-name (:vcs_url build)) #"/")]
     {:username username
      :project project
      :build-num (or (:build_num build) (:build-num build))
      :vcs-type (or (:vcs_type build) "github")}))
  ([build container-index]
   (assoc (build-parts build) :container-index container-index)))

(defn usage-queue-build-index-from-build-parts [state parts]
  "Returns index if there is a usage-queued build showing which
  matches the provided build-id."
  (when-let [builds (seq (get-in state state/usage-queue-path))]
    (find-index #(= parts (build-parts %)) builds)))

(defn add-envvar-to-map
  [m {:keys [name value]}]
  (assoc m
    name value))

(defn envvars-seq-to-map
  [envvar-resp]
  (reduce add-envvar-to-map
          {}
          envvar-resp))

(defn envvars-map-to-seq
  [envvar-map]
  (for [[k v] envvar-map]
    {:name k :value v}))
