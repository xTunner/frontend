(ns frontend.controllers.api.contexts
  (:require [frontend.api.contexts :as api.contexts]
            [frontend.controllers.api.impl :as impl]
            [frontend.state :as state]))

(defn- success
  [callback state resp]
  (let [new-state (update-in state state/org-contexts-path merge resp)]
    (callback :success)
    new-state))

(defmethod impl/api-event [::api.contexts/create :success]
  [_ _ _ {resp :resp {callback :callback-fn} :context} state]
  (success callback state resp))

(defmethod impl/api-event [::api.contexts/fetch :success]
  [_ _ _ {resp :resp {callback :callback-fn} :context} state]
  (success callback state resp))

(defmethod impl/api-event [::api.contexts/store :success]
  [_ _ _ {resp :resp {callback :callback-fn} :context} state]
  (success callback state resp))

(defmethod impl/api-event [::api.contexts/remove :success]
  [_ _ _ {resp :resp {callback :callback-fn} :context} state]
  (success callback state resp))
