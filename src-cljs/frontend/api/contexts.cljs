(ns frontend.api.contexts
  "Calls and helpers for interacting with Contexts in the
  Query API."
  (:require [clojure.spec.alpha :as s :include-macros true]
            [frontend.api.path :as path]
            [frontend.utils.ajax :as ajax]))

(s/def ::variable string?)
(s/def ::value string?)
(s/def ::envvar (s/keys :req-un [::variable ::value]))
(s/def ::resources (s/coll-of ::envvar))

(s/def ::vcs_type #{"github" "bitbucket"})
(s/def ::name string?)
(s/def ::analytics_id string?)
(s/def ::organization (s/keys :req-un [::vcs_type ::name ::analytics_id]))

(defn- make-context-call
  "Context calls all have a similar shape [:contexts {:action Keyword <org-info>}]
  Instead of duplicating this we can just setup the basic shape of the request simply."
  [{api-ch   :api-ch
    api-id   :id
    callback :callback-fn}
   {vcs-type         :vcs_type
    org-name         :name
    org-analytics-id :analytics_id}
   action resources]
  (ajax/ajax :post
             path/query-api
             api-id
             api-ch
             :context {:callback-fn callback}
             :format :transit
             :params
             [:contexts {:action                action
                         :organization-ref      org-analytics-id
                         :organization-vcs-type vcs-type
                         :organization-name     org-name
                         :resources             resources}]))

(defn create-context
  "Given the api-ch and basic information about an organization,
  upsert the specified organization's context"
  ([api-ch org]
   (create-context api-ch (constantly nil) org))
  ([api-ch callback org]
   (make-context-call {:api-ch      api-ch
                       :id          ::create
                       :callback-fn callback}
                      org
                      :create [])))

(defn fetch-context
  "Given the api-ch and basic information about an organization,
  fetch the specified organization's context.
  If no context exists, throws a 404."
  ([api-ch org]
   (fetch-context api-ch (constantly nil) org))
  ([api-ch callback org]
   (make-context-call {:api-ch      api-ch
                       :id          ::fetch
                       :callback-fn callback}
                      org
                      :fetch [])))

(defn store-resources
  "Given the api-ch and basic information about an organization,
  store resources of the form [{:variable String :value String|Number|Nil} ...]
  in the specified organization's context.
  If no context exists, throws a 404."
  ([api-ch org resources]
   (store-resources api-ch (constantly nil) org resources))
  ([api-ch callback org resources]
   (make-context-call {:api-ch      api-ch
                       :id          ::store
                       :callback-fn callback}
                      org
                      :store resources)))

(defn remove-resources
  "Given the api-ch and basic information about an organization,
  remove resources of the form [{:variable String} ...]
  from the specified organization's context.
  If no context exists, throws a 404."
  ([api-ch org resources]
   (remove-resources api-ch (constantly nil) org resources))
  ([api-ch callback org resources]
   (make-context-call {:api-ch      api-ch
                       :id          ::remove
                       :callback-fn callback}
                      org
                      :remove resources)))

(s/fdef
  create-context
  :args (s/cat :chan identity
               :org ::organization)
  :ret ::ajax/ajax-request)

(s/fdef
  fetch-context
  :args (s/cat :chan identity
               :org ::organization)
  :ret ::ajax/ajax-request)

(s/fdef
  store-resources
  :args (s/cat :chan identity
               :org ::organization
               :resources ::resources)
  :ret ::ajax/ajax-request)

(s/fdef
  remove-resources
  :args (s/cat :chan identity
               :org ::organization
               :resources ::resources)
  :ret ::ajax/ajax-request)
