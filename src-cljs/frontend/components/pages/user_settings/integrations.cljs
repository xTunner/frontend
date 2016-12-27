(ns frontend.components.pages.user-settings.integrations
  (:require [frontend.models.user :as user]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [html]]))

;; Stub implementation of the Integrations page.
(defui Subpage
  static om-next/IQuery
  (query [this]
    [{:app/current-user [:user/bitbucket-authorized?]}])
  Object
  (render [this]
    (html
     [:div
      [:pre [:code (pr-str (om-next/props this))]]
      "Integrations!"])))
