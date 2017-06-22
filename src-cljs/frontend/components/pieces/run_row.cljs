(ns frontend.components.pieces.run-row
  (:require [clojure.spec :as s :include-macros true]
            [clojure.string :as string]
            [clojure.test.check.generators :as gen]
            [frontend.analytics :as analytics]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.datetime :as datetime]
            [frontend.gencard :as gc]
            [frontend.models.build :as build-model]
            [frontend.routes :as routes]
            [frontend.timer :as timer]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [devcards.core :as dc :refer [defcard]]))

(defn loading-placeholder [width]
  (component (html [:div {:style {:width width}}])))

(defn loading-circle []
  (component (html [:span (icon/simple-circle)])))

(defn- status-class [run-status]
  (case run-status
    :run-status/running :status-class/running
    :run-status/succeeded :status-class/succeeded
    :run-status/failed :status-class/failed
    (:run-status/canceled :run-status/not-run) :status-class/stopped
    :run-status/needs-setup :status-class/setup-needed
    :run-status/on-hold :status-class/on-hold))

(def ^:private cancelable-statuses #{:run-status/not-run
                                     :run-status/running
                                     :run-status/on-hold})

(def ^:private rerunnable-statuses #{:run-status/succeeded
                                     :run-status/failed
                                     :run-status/canceled})

(def ^:private rerunnable-from-start-statuses #{:run-status/failed})

(defn- run-prs
  "A om-next compatible version of
  `frontend.components.builds-table/pull-requests`."
  [parent-component pull-requests]
  (when-let [urls (seq (map :pull-request/url pull-requests))]
    (html
     [:span.metadata-item.pull-requests {:title "Pull Requests"}
      (icon/git-pull-request)
      (interpose
       ", "
       (for [url urls
             ;; WORKAROUND: We have/had a bug where a PR URL would be reported as nil.
             ;; When that happens, this code blows up the page. To work around that,
             ;; we just skip the PR if its URL is nil.
             :when url]
         [:a
          {:href url
           :on-click #(analytics/track! parent-component
                                        {:event-type :pr-link-clicked})}
          "#"
          (gh-utils/pull-request-number url)]))])))

(defn- commit-link
  "Om Next compatible version of `frontend.components.builds-table/commits`."
  [parent-component vcs-type org repo sha]
  (when (and vcs-type org repo sha)
    (let [pretty-sha (build-model/github-revision {:vcs_revision sha})]
      (html
       [:span.metadata-item.revision
        [:i.octicon.octicon-git-commit]
        [:a {:title pretty-sha
             :href (build-model/commit-url {:vcs_revision sha
                                            :vcs_url (vcs-url/vcs-url vcs-type
                                                                      org
                                                                      repo)})
             :on-click #(analytics/track! parent-component
                                          {:event-type :revision-link-clicked})}
         pretty-sha]]))))

(defn- transact-run-mutate [component mutation]
  (om-next/transact!

   ;; We transact on the reconciler, not the component; otherwise the
   ;; component's props render as nil for a moment. This is odd.
   ;;
   ;; It looks like the transaction drops the run from the app state.
   ;; Transacting on the component means the component immediately re-reads, so
   ;; it immediately renders nil. Moments later, the query is read from the
   ;; server again, delivering new data to the app state, and the component
   ;; renders with data again.
   ;;
   ;; When we transact on the reconciler, we simply avoid rendering the first
   ;; time, during the window when the run is missing. Of course, it shouldn't
   ;; be missing in the first place.
   ;;
   ;; tl;dr: there's a bug in here, but it's not clear what, and this works fine
   ;; for now.
   (om-next/get-reconciler component)

   ;; It's not clear why we have to manually transform-reads---Om should do that
   ;; for us if we give a simple keyword---but it doesn't appear to be doing it,
   ;; so we do it. This is another bug we're punting on.
   (om-next/transform-reads
    (om-next/get-reconciler component)
    [mutation
     ;; We queue the entire page to re-read using :compassus.core/route-data.
     ;; Ideally we'd know what specifically to re-run, but there are now
     ;; several keys the new run could show up under. (Aliases also complicate
     ;; this, and solving that problem is not something we want to spend time
     ;; on yet.) Re-reading the entire query here seems like a good idea
     ;; anyhow.
     :compassus.core/route-data])))

(defn- transact-run-retry
  [component run-id jobs]
  (transact-run-mutate component `(run/retry {:run/id ~run-id :run/jobs ~jobs})))

(defn- transact-run-cancel
  [component run-id vcs-type org-name project-name]
  (transact-run-mutate component `(run/cancel {:run/id ~run-id})))


(defui ^:once RunRow
  ;; NOTE: this is commented out until bodhi handles queries for components with idents first
  ;; static om-next/Ident
  ;; (ident [this props]
  ;;   [:run/by-id (:run/id props)])
  static om-next/IQuery
  (query [this]
    [:run/id
     :run/name
     :run/status
     :run/started-at
     :run/stopped-at
     {:run/errors [:workflow-error/message]}
     {:run/jobs [:job/id]}
     {:run/trigger-info [:trigger-info/vcs-revision
                         :trigger-info/subject
                         :trigger-info/body
                         :trigger-info/branch
                         {:trigger-info/pull-requests [:pull-request/url]}]}
     {:run/project [:project/name
                    {:project/organization [:organization/name
                                            :organization/vcs-type]}]}])
  Object
  (render [this]
    (component
      (let [{:keys [::loading?]} (om-next/get-computed this)
            {:keys [run/id
                    run/errors
                    run/status
                    run/started-at
                    run/stopped-at
                    run/trigger-info
                    run/jobs]
             run-name :run/name
             {project-name :project/name
              {org-name :organization/name
               vcs-type :organization/vcs-type} :project/organization} :run/project}
            (om-next/props this)
            {commit-sha :trigger-info/vcs-revision
             commit-body :trigger-info/body
             commit-subject :trigger-info/subject
             pull-requests :trigger-info/pull-requests
             branch :trigger-info/branch} trigger-info
            run-status-class (when-not loading?
                               (status-class status))]

        (card/full-bleed
         (element :content
           (html
            [:div (when loading? {:class "loading"})
             [:.inner-content
              [:.status-and-button
               [:div.status {:class (if loading? "loading" (name run-status-class))}
                [(if id :a.exception :div)
                 (when id {:href (routes/v1-run-path id)
                           :on-click #(analytics/track!
                                       this
                                       {:event-type :run-status-clicked})})
                 [:span.status-icon
                  (if loading?
                    (icon/simple-circle)
                    (case run-status-class
                      :status-class/failed (icon/status-failed)
                      :status-class/setup-needed (icon/status-setup-needed)
                      :status-class/stopped (icon/status-canceled)
                      :status-class/succeeded (icon/status-passed)
                      :status-class/running (icon/status-running)
                      :status-class/on-hold (icon/status-on-hold)))]
                 [:.status-string
                  (when-not loading?
                    (string/replace (name status) #"-" " "))]]]
               (cond
                 loading? [:div.action-button [:svg]]
                 (contains? cancelable-statuses status)
                 [:div.action-button.cancel-button
                  {:on-click (fn [_]
                               (transact-run-cancel this id vcs-type org-name project-name)
                               (analytics/track! this {:event-type :cancel-clicked}))}
                  (icon/status-canceled)
                  [:span "cancel"]]
                 (contains? rerunnable-statuses status)
                 [:div.action-button.rebuild-button.dropdown
                  (icon/rebuild)
                  [:span "Rerun"]
                  [:i.fa.fa-chevron-down.dropdown-toggle {:data-toggle "dropdown"}]
                  [:ul.dropdown-menu.pull-right
                   (when (rerunnable-from-start-statuses status)
                     [:li
                      [:a
                       {:on-click (fn [_event]
                                    (transact-run-retry this id [])
                                    (analytics/track! this
                                                      {:event-type :rerun-clicked
                                                       :properties {:is-from-failed true}}))}
                       "Rerun failed jobs"]])
                   [:li
                    [:a
                     {:on-click (fn [_event]
                                  (transact-run-retry this id jobs)
                                  (analytics/track! this
                                                    {:event-type :rerun-clicked
                                                     :properties {:is-from-failed false}}))}
                     "Rerun from beginning"]]]])]
              [:div.run-info
               [:div.build-info-header
                [:div.contextual-identifier
                 [(if id :a :span)
                  (when id {:href (routes/v1-run-path id)
                            :on-click #(analytics/track! this
                                                         {:event-type :run-link-clicked})})
                  (if loading?
                    (loading-placeholder 300)
                    [:span branch " / " run-name])]]]
               [:div.recent-commit-msg
                [:span.recent-log
                 {:title (when commit-body
                           commit-body)}
                 (if loading?
                   (loading-placeholder 200)
                   (when commit-subject
                     commit-subject))]]]
              [:div.metadata
               [:div.metadata-row.timing
                [:span.metadata-item.recent-time.start-time
                 (if loading?
                   (loading-circle)
                   [:i.material-icons "today"])
                 (if started-at
                   [:span {:title (str "Started: " (datetime/full-datetime started-at))}
                    (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})
                    [:span " ago"]]
                   "-")]
                [:span.metadata-item.recent-time.duration
                 (if loading?
                   (loading-circle)
                   [:i.material-icons "timer"])
                 (if stopped-at
                   [:span {:title (str "Duration: "
                                       (datetime/as-duration (- (.getTime stopped-at)
                                                                (.getTime started-at))))}
                    (build-legacy common/updating-duration {:start started-at
                                                            :stop stopped-at})]
                   "-")]]
               [:div.metadata-row.pull-revision
                (if loading?
                  [:span.metadata-item.pull-requests (loading-circle)]
                  (run-prs this pull-requests))
                (if loading?
                  [:span.metadata-item.revision (loading-circle)]
                  (commit-link this
                               vcs-type
                               org-name
                               project-name
                               commit-sha))]]]])))))))

(def run-row (om-next/factory RunRow {:keyfn :run/id}))

(def loading-run-row* (om-next/factory RunRow))
(defn loading-run-row [] (loading-run-row* (om-next/computed {} {::loading? true})))

(dc/do
  (defcard run-row
    (binding [om-next/*shared* {:timer-atom (timer/initialize)}]
      (run-row {:run/id (random-uuid)
                :run/name "a-workflow"
                :run/status :run-status/succeeded
                :run/started-at #inst "2017-05-31T18:59:19.517-00:00"
                :run/stopped-at #inst "2017-05-31T19:59:19.517-00:00"
                :run/trigger-info {:trigger-info/vcs-revision "abcd123"
                                   :trigger-info/subject "Changed something."
                                   :trigger-info/body "Actually, changed a lot of things."
                                   :trigger-info/branch "change-stuff"
                                   :trigger-info/pull-requests [{:pull-request/url "https://github.com/acme/anvil/pull/1974"}]}
                :run/project {:project/name "anvil"
                              :project/organization {:organization/name "acme"
                                                     :organization/vcs-type :github}}})))

  (def lorem-words
    ["alias"
     "consequatur"
     "aut"
     "perferendis"
     "sit"
     "voluptatem"
     "accusantium"
     "doloremque"
     "aperiam"
     "eaque"
     "ipsa"
     "quae"
     "ab"
     "illo"
     "inventore"
     "veritatis"
     "et"
     "quasi"
     "architecto"
     "beatae"
     "vitae"
     "dicta"
     "sunt"
     "explicabo"
     "aspernatur"
     "aut"
     "odit"
     "aut"
     "fugit"
     "sed"
     "quia"
     "consequuntur"
     "magni"
     "dolores"
     "eos"
     "qui"
     "ratione"
     "voluptatem"
     "sequi"
     "nesciunt"
     "neque"
     "dolorem"
     "ipsum"
     "quia"
     "dolor"
     "sit"
     "amet"
     "consectetur"
     "adipisci"
     "velit"
     "sed"
     "quia"
     "non"
     "numquam"
     "eius"
     "modi"
     "tempora"
     "incidunt"
     "ut"
     "labore"
     "et"
     "dolore"
     "magnam"
     "aliquam"
     "quaerat"
     "voluptatem"
     "ut"
     "enim"
     "ad"
     "minima"
     "veniam"
     "quis"
     "nostrum"
     "exercitationem"
     "ullam"
     "corporis"
     "nemo"
     "enim"
     "ipsam"
     "voluptatem"
     "quia"
     "voluptas"
     "sit"
     "suscipit"
     "laboriosam"
     "nisi"
     "ut"
     "aliquid"
     "ex"
     "ea"
     "commodi"
     "consequatur"
     "quis"
     "autem"
     "vel"
     "eum"
     "iure"
     "reprehenderit"
     "qui"
     "in"
     "ea"
     "voluptate"
     "velit"
     "esse"
     "quam"
     "nihil"
     "molestiae"
     "et"
     "iusto"
     "odio"
     "dignissimos"
     "ducimus"
     "qui"
     "blanditiis"
     "praesentium"
     "laudantium"
     "totam"
     "rem"
     "voluptatum"
     "deleniti"
     "atque"
     "corrupti"
     "quos"
     "dolores"
     "et"
     "quas"
     "molestias"
     "excepturi"
     "sint"
     "occaecati"
     "cupiditate"
     "non"
     "provident"
     "sed"
     "ut"
     "perspiciatis"
     "unde"
     "omnis"
     "iste"
     "natus"
     "error"
     "similique"
     "sunt"
     "in"
     "culpa"
     "qui"
     "officia"
     "deserunt"
     "mollitia"
     "animi"
     "id"
     "est"
     "laborum"
     "et"
     "dolorum"
     "fuga"
     "et"
     "harum"
     "quidem"
     "rerum"
     "facilis"
     "est"
     "et"
     "expedita"
     "distinctio"
     "nam"
     "libero"
     "tempore"
     "cum"
     "soluta"
     "nobis"
     "est"
     "eligendi"
     "optio"
     "cumque"
     "nihil"
     "impedit"
     "quo"
     "porro"
     "quisquam"
     "est"
     "qui"
     "minus"
     "id"
     "quod"
     "maxime"
     "placeat"
     "facere"
     "possimus"
     "omnis"
     "voluptas"
     "assumenda"
     "est"
     "omnis"
     "dolor"
     "repellendus"
     "temporibus"
     "autem"
     "quibusdam"
     "et"
     "aut"
     "consequatur"
     "vel"
     "illum"
     "qui"
     "dolorem"
     "eum"
     "fugiat"
     "quo"
     "voluptas"
     "nulla"
     "pariatur"
     "at"
     "vero"
     "eos"
     "et"
     "accusamus"
     "officiis"
     "debitis"
     "aut"
     "rerum"
     "necessitatibus"
     "saepe"
     "eveniet"
     "ut"
     "et"
     "voluptates"
     "repudiandae"
     "sint"
     "et"
     "molestiae"
     "non"
     "recusandae"
     "itaque"
     "earum"
     "rerum"
     "hic"
     "tenetur"
     "a"
     "sapiente"
     "delectus"
     "ut"
     "aut"
     "reiciendis"
     "voluptatibus"
     "maiores"
     "doloribus"
     "asperiores"
     "repellat"])

  (s/def :run/entity (s/and
                      (s/keys :req [:run/id
                                    :run/name
                                    :run/status
                                    :run/started-at
                                    :run/stopped-at
                                    :run/trigger-info
                                    :run/project])
                      (fn [{:keys [:run/status :run/started-at :run/stopped-at]}]
                        (case status
                          (:run-status/running
                           :run-status/not-run
                           :run-status/needs-setup)
                          (and started-at (nil? stopped-at))

                          (:run-status/succeeded
                           :run-status/failed
                           :run-status/canceled)
                          (and started-at stopped-at (< started-at stopped-at))))))

  (s/def :run/trigger-info (s/keys :req [:trigger-info/vcs-revision
                                         :trigger-info/subject
                                         :trigger-info/body
                                         :trigger-info/branch
                                         :trigger-info/pull-requests]))

  (s/def :run/project (s/keys :req [:project/name
                                    :project/organization]))

  (s/def :project/organization (s/keys :req [:organization/name
                                             :organization/vcs-type]))

  (s/def :run/id uuid?)
  (s/def :run/name (s/and string? seq))
  (s/def :run/status #{:run-status/running
                       :run-status/succeeded
                       :run-status/failed
                       :run-status/canceled
                       :run-status/not-run
                       :run-status/needs-setup})
  (s/def :run/started-at (s/nilable inst?))
  (s/def :run/stopped-at (s/nilable inst?))
  (s/def :trigger-info/vcs-revision (s/with-gen
                                      (s/and string? (partial re-matches #"[0-9a-f]{40}"))
                                      #(gen/fmap (fn [n] (.toString n 16))
                                                 (gen/choose 0 (dec (Math/pow 2 160))))))
  (s/def :trigger-info/subject string?)
  (s/def :trigger-info/body string?)
  (s/def :trigger-info/branch (s/and string? seq))
  ;; TODO: This generates lots of unnecessary morphs.
  (s/def :trigger-info/pull-requests #_(s/every (s/keys :req [:pull-request/url])) #{[]})
  (s/def :pull-request/url string?)
  (s/def :project/name (s/and string? seq))
  (s/def :organization/name (s/and string? seq))
  (s/def :organization/vcs-type #{:github :bitbucket})

  (defn dashed-lorem []
    (gen/fmap (partial string/join "-") (gen/vector (gen/elements lorem-words))))

  (defn lorem-sentence []
    (gen/fmap
     (fn [[first-word & words]]
       (if first-word
         (str (string/join " " (cons (string/capitalize first-word) words)) ".")
         ""))
     (gen/vector (gen/elements lorem-words))))

  ;; https://stackoverflow.com/questions/25324082/index-of-vector-in-clojurescript/32885837#32885837
  (defn- index-of [coll value]
    (some (fn [[idx item]] (if (= value item) idx))
          (map-indexed vector coll)))

  (defcard generated-run-rows
    (binding [om-next/*shared* {:timer-atom (timer/initialize)}]
      (let [statuses [:run-status/needs-setup
                      :run-status/not-run
                      :run-status/running
                      :run-status/succeeded
                      :run-status/failed
                      :run-status/canceled]
            sort-by-status (partial sort-by (comp (partial index-of statuses) :run/status))]
        (html
         [:div
          (let [data (gc/morph-data run-row :run/entity {:run/name #(dashed-lorem)
                                                         :trigger-info/branch #(dashed-lorem)
                                                         :trigger-info/subject #(lorem-sentence)})]
            (if data
              (card/collection (map run-row (sort-by-status data)))
              "Infinite morphs!"))]))))

  (defcard loading-run-row
    (loading-run-row)))
