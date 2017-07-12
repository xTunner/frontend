(ns frontend.controllers.api.contexts
  (:require [frontend.api.contexts :as api.contexts]
            [frontend.controllers.api.impl :as impl]
            [frontend.state :as state]))

(defmethod impl/api-event [::api.contexts/create :success]
  [_ _ _ {:keys [resp]} state]
  (update-in state state/org-contexts-path merge resp))

(defmethod impl/api-event [::api.contexts/fetch :success]
  [_ _ _ {:keys [resp]} state]
  (update-in state state/org-contexts-path merge resp))

(defmethod impl/api-event [::api.contexts/store :success]
  [_ _ _ {:keys [resp]} state]
  (update-in state state/org-contexts-path merge resp))

(defmethod impl/api-event [::api.contexts/remove :success]
  [_ _ _ {:keys [resp]} state]
  (update-in state state/org-contexts-path merge resp))
