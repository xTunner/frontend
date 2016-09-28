(ns frontend.parser
  (:require [frontend.analytics.core :as analytics]
            [frontend.components.app :as app]
            [frontend.routes :as routes]
            [om.next :as om-next]
            [om.next.impl.parser :as parser]
            [om.util :as om-util]))

(defn register-page-key!
  "Registers a key as a routing key for a page. These should be the keys of the
  Compassus route map. Reading keys under a page key will function like reading
  that key from the root."
  [page-key]
  (derive page-key ::page))

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
;; multimethods, each with a default: read-local and read-remote.
;;
;; The values they return will become the values of the :value and :remote keys,
;; respectively, in the read function's result.

(defmulti read-local om-next/dispatch)
(defmulti read-remote om-next/dispatch)

;; Most keys resolve locally as an ordinary db->tree.
(defmethod read-local :default
  [{:keys [state query] :as env} key params]
  (let [st @state]
    (om-next/db->tree query (get st key) st)))

;; Most keys pass their queries on to the remote. We add :query-root true so
;; that the key will be sent to the remote as one of the root keys in the
;; query (using om-next/process-roots). This only matters when the parser is
;; being called recursively--that is, when another read-remote implementation
;; calls the parser on its subquery, as ::page does. (If the parser isn't being
;; called recursively, that means this is already a root key, and
;; adding :query-root changes nothing.)
(defmethod read-remote :default
  [{:keys [ast] :as env} key params]
  (assoc ast :query-root true))

;; Page queries flatten their reads by calling the parser recursively. That is,
;; any key which can be read from the root can also be read from here. So if
;;
;; [{:some/data [:some/property]}]
;; ;; => {:some/data {:some/property "a value"}}
;;
;; [{:a/page-key [{:some/data [:some/property]}]}]
;; ;; => {:a/page-key {:some/data {:some/property "a value"}}}
(defmethod read-local ::page
  [{:keys [parser query] :as env} key params]
  (parser env query))

;; Similarly, page queries call the parser recursively to build the remote
;; query. If all of the subquery keys eliminate themselves from the remote query
;; entirely, we eliminate this key as well by returning nil. Therefore, we never
;; end up with an empty subquery--that is, something like:
;;
;; [{:a/page-key []}]
(defmethod read-remote ::page
  [{:keys [parser query target] :as env} key params]
  (let [subquery (parser env query target)]
    (when (seq subquery)
      (parser/expr->ast {key subquery}))))


(def
  ^{:private true
    :doc
    "Keys under :app/current-user which are fed by the page's renderContext,
     and shouldn't be fetched from the remote by API"}
  render-context-keys
  #{:user/login
    :user/bitbucket-authorized})


;; :app/current-user works much like :default, but it's a bit of a special case,
;; as some of its data is never fetched by the remote, and only exists in the
;; initial app state, added from the page's renderContext. We exclude those keys
;; here so we don't try to read them remotely.
(defmethod read-remote :app/current-user
  [{:keys [ast] :as env} key params]
  (-> ast
      (assoc :query-root true)
      ;; Don't pass renderContext keys on to the remote. They're
      ;; local-only.
      (update :children
              (fn [children]
                (into []
                      (remove #(contains? render-context-keys (:key %)))
                      children)))
      recalculate-query))

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

;; The keys in :app/route-data have idents for values. If we query through
;; them, we replace the key with the current ident value before passing it
;; on to the remote. That is, if the UI queries for
;;
;; [{:app/route-data [{:route-data/widget [:widget/name]}]}]
;;
;; and the app state contains
;;
;; {;; ...
;;  :app/route-data {:route-data/widget [:widget/by-id 5]}
;;  ;; ...
;;  }
;;
;; we rewrite the query for the remote to be
;;
;; [{:app/route-data [^:query-root {[:widget/by-id 5] [:widget/name]}]}]
;;
;; Then the remote will look up the name of the current widget.
;;
;; Note that the :default case already handles the read-local
;; for :app/route-data perfectly.
(defmethod read-remote :app/route-data
  [{:keys [state ast] :as env} key params]
  (-> ast
      (update :children
              (fn [children]
                (into []
                      (keep
                       #(let [ident (get-in @state [key (:key %)])]
                          (when ident
                            (assert (om-util/ident? ident)
                                    (str "The values stored in " key " must be idents."))
                            ;; Replace the :key and :dispatch-key with the
                            ;; ident we've found, and make them :query-roots.
                            (assoc %
                                   :key ident
                                   :dispatch-key (first ident)
                                   :query-root true))))
                      children)))
      recalculate-query))

;; This is solely to support frontend.components.app/Loading's do-nothing query,
;; until a later version of Compassus allows it to have no query.
(defmethod read-local :nothing/nothing [env key params] nil)
(defmethod read-remote :nothing/nothing [env key params] nil)


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
  [{:keys [state] :as env} key params]
  {:action #(let [route-data (cond-> {}
                               (contains? params :organization)
                               (assoc :projects-page/organization
                                      [:organization/by-vcs-type-and-name
                                       (select-keys (:organization params)
                                                    [:organization/vcs-type :organization/name])]))
                  ;; Once Compassus lets us change the route and set route
                  ;; data in one transaction, we can use the :route from the
                  ;; env and stop passing it as a param. See
                  ;; frontend.routes/open for more info.
                  route (:route params)]
              (swap! state assoc :app/route-data route-data)
              (analytics/track {:event-type :pageview
                                :navigation-point route
                                :subpage :default
                                :properties {:user (get-in @state [:app/current-user :user/login])
                                             :view route
                                             :org (get-in params [:organization :organization/name])}}))})


(def parser (om-next/parser {:read read :mutate mutate}))
