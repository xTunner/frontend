(ns frontend.parser
  (:require [compassus.core :as compassus]
            [frontend.analytics.core :as analytics]
            [frontend.components.app :as app]
            [frontend.routes :as routes]
            [om.next :as om-next]
            [om.next.impl.parser :as parser]
            [om.util :as om-util]))

(defn- recalculate-query
  "Each node of an AST has a :query key which is the query form of
  its :children. This is a kind of denormalization. If we change the :children,
  the :query becomes out of date, and Om will use the old :query rather than the
  new :children. This probably represents an Om bug of some kind.

  As a workaround, any time you change an AST's :children, run it through
  recalculate-query before returning it."
  [ast]
  (-> ast
      om-next/ast->query
      parser/expr->ast))


;; Many of our keys have the same local reading behavior, and many of our keys
;; have the same remote reading behavior, but they're not always the same keys.
;; For our app, then, it makes sense to divide the read function into two
;; multimethods: read-local and read-remote.
;;
;; The values they return will become the values of the :value and :remote keys,
;; respectively, in the read function's result.

(defmulti read-local om-next/dispatch)
(defmulti read-remote om-next/dispatch)

;; Most keys resolve locally as an ordinary db->tree or as a simple key lookup.
(defmethod read-local :default
  [{:keys [state query ast] :as env} key params]
  (let [st @state
        value-at-key (get st key)]
    (case (:type ast)
      ;; For a :prop (eg. [:some/value]), just look up and return the value.
      :prop value-at-key
      ;; For a :join (eg. [{:some/complex-value [:with/a :deeper/query]}}]),
      ;; resolve the deeper query with db->tree.
      :join (om-next/db->tree query value-at-key st))))

;; When adding a new key, be sure to add a read-remote implementation. Returning
;; true will pass the entire query on to the remote send function. Returning
;; false will send nothing to the remote. Returning a modified AST will send
;; that modified query to the remote.
(defmethod read-remote :default [env key params]
  (throw (js/Error. (str "No remote behavior defined in the parser for " (pr-str key) "."))))


(def
  ^{:private true
    :doc
    "Keys under :app/current-user which are fed by the page's renderContext,
     and shouldn't be fetched from the remote by API"}
  render-context-keys
  #{:user/login
    :user/bitbucket-authorized?})


;; Some of :app/current-user's data is never fetched by the remote, and only
;; exists in the initial app state, added from the page's renderContext. We
;; exclude those keys here so we don't try to read them remotely.
(defmethod read-remote :app/current-user
  [{:keys [ast] :as env} key params]
  (let [new-ast (update ast :children
                        (fn [children]
                          (into []
                                (remove #(contains? render-context-keys (:key %)))
                                children)))]
    ;; Only include this key in the remote query if there are any children left.
    (if (seq (:children new-ast))
      (recalculate-query new-ast)
      nil)))

;; :legacy/state reads the entire map under :legacy/state in the app state. It
;; does no db->tree massaging, because the legacy state lives in the om.core
;; world and doesn't expect anything like that.
(defmethod read-local :legacy/state
  [{:keys [state] :as env} key params]
  ;; Don't include :inputs; it's not meant to be passed into the top of the
  ;; legacy app, but instead is accessed directly by
  ;; frontend.components.inputs/get-inputs-from-app-state.
  (dissoc (get @state key) :inputs))

;; The :legacy/state is never read remotely.
(defmethod read-remote :legacy/state
  [env key params]
  nil)


;; The subpage is a purely local concern.
(defmethod read-remote :app/subpage-route
  [env key params]
  nil)

;; :app/subpage-route-data expects to have a union query (a map), where each key
;; is a subpage route and each value is the query for that route's subpage.
;;
;; :app/subpage-route-data will read the query for whichever subpage route is
;; current, by calling the parser recursively. (This is exactly how Compassus works.)
(defmethod read-local :app/subpage-route-data
  [{:keys [state query parser] :as env} key params]
  (let [subpage-route (get @state :app/subpage-route)
        subpage-query (get query subpage-route)]
    ;; Make the subpage's query against the parser.
    (parser env subpage-query nil)))

;; Subpage data can't read from the remote server yet.
(defmethod read-remote :app/subpage-route-data
  [env key params]
  nil)

(defn- route-data-ident
  "Returns an ident that addresses the given route-data `subkey`, according to
  the `route-data`. That is, when `subkey` is `:route-data/organization`,
  returns an ident which addresses the organization identified in the
  `route-data`. If `route-data` doesn't provide enough data to identify the
  entity that `subkey` would refer to, returns `nil`."
  [subkey route-data]
  (case subkey
    :route-data/organization (when (every? #(contains? route-data %)
                                           [:organization/vcs-type
                                            :organization/name])
                               [:organization/by-vcs-type-and-name
                                (select-keys route-data
                                             [:organization/vcs-type
                                              :organization/name])])
    :route-data/project (when (every? #(contains? route-data %)
                                      [:organization/vcs-type
                                       :organization/name
                                       :project/name])
                          [:project/by-org-and-name
                           (select-keys route-data
                                        [:organization/vcs-type
                                         :organization/name
                                         :project/name])])
    :route-data/workflow (when (every? #(contains? route-data %)
                                       [:organization/vcs-type
                                        :organization/name
                                        :project/name])
                           [:project/by-org-and-name
                            {:project/name (:project/name route-data)
                             :project/organization (select-keys route-data
                                                                [:organization/vcs-type
                                                                 :organization/name])}])
    :route-data/run (when (every? #(contains? route-data %)
                                  [:run/id])
                      [:run/by-id (:run/id route-data)])))

(defmethod read-local :app/route-data
  [{:keys [state query] :as env} key params]
  (let [st @state]
    (reduce (fn [res expr]
              (let [subkey (om-util/join-key expr)
                    subquery (om-util/join-value expr)
                    ident (route-data-ident subkey (:app/route-data st))]
                (cond-> res
                  ident (assoc subkey (om-next/db->tree subquery (get-in st ident) st)))))
            {}
            query)))

(defmethod read-remote :app/route-data
  [{:keys [state query] :as env} key params]
  (let [st @state
        remote-query (reduce (fn [res expr]
                               (let [subkey (om-util/join-key expr)
                                     subquery (om-util/join-value expr)
                                     ident (route-data-ident subkey (:app/route-data st))]
                                 (cond-> res
                                   ident (conj ^:query-root {ident subquery}))))
                             []
                             query)]
    (when (seq remote-query)
      (parser/expr->ast
       {key remote-query}))))


(defn read [{:keys [target] :as env} key params]
  ;; Dispatch to either read-local or read-remote. Remember that, despite the
  ;; fact that a read function can return both a :value and a :remote entry in a
  ;; single map, the parser is actually only looking for one or the other during
  ;; a given call, and the presence or absence of target tells you which it is.
  (case target
    nil {:value (read-local env key params)}
    :remote {:remote (read-remote env key params)}))


(defmulti mutate om-next/dispatch)

;; frontend.routes/set-data sets the :app/route-data during navigation.
(defmethod mutate `routes/set-data
  [{:keys [state route] :as env} key {:keys [subpage route-data]}]
  {:action (fn []
             (swap! state #(-> %
                               (assoc :app/subpage-route subpage
                                      :app/route-data route-data)

                               ;; Clean up the legacy state so it doesn't leak
                               ;; from the previous page. This goes away when
                               ;; the legacy state dies. In the Om Next world,
                               ;; all route data is in :app/route-data, and is
                               ;; replaced completely on each route change.
                               (update :legacy/state dissoc
                                       :navigation-point
                                       :navigation-data
                                       :current-build-data
                                       :current-org-data
                                       :current-project-data)))
             (analytics/track {:event-type :pageview
                               :navigation-point route
                               :subpage :default
                               :properties {:user (get-in @state [:app/current-user :user/login])
                                            :view route
                                            :org (get-in route-data [:route-data/organization :organization/name])}}))})

(def parser (compassus/parser {:read read
                               :mutate mutate
                               :route-dispatch false}))
