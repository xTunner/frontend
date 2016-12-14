(ns frontend.components.pages.user-settings
  (:require [frontend.analytics :as analytics]
            [frontend.api :as api]
            [frontend.components.account :as old-components]
            [frontend.components.common :as common]
            [frontend.components.templates.main :as main-template]
            [frontend.state :as state]
            [frontend.utils :refer [set-page-title!]]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.seq :refer [select-in]]
            [om.core :as om :include-macros true]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component element html]]))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ;; NB: Every Page *must* query for {:legacy/state [*]}, to make it available
    ;; to frontend.components.header/header. This is necessary until the
    ;; wrapper, not the template, renders the header.
    ;; See https://circleci.atlassian.net/browse/CIRCLE-2412
    ['{:legacy/state [*]}
     {:app/current-user [:user/login]}
     :app/subpage-route])
  analytics/Properties
  (properties [this]
    (let [props (om-next/props this)]
      {:user (get-in props [:app/current-user :user/login])
       :view :account}))
  Object
  (componentDidMount [this]
    (set-page-title! "Account")

    ;; Replicates the old behavior of post-navigated-to! :account.
    ;; This will go away as the subpages which use this data move to Om Next
    ;; themselves.
    (let [api-ch (om-next/shared this [:comms :api])]
      (when-not (seq (get-in (:legacy/state (om-next/props this)) state/projects-path))
        (api/get-projects api-ch))
      (ajax/ajax :get "/api/v1/sync-github" :me api-ch)
      (api/get-orgs api-ch :include-user? true)
      (ajax/ajax :get "/api/v1/user/token" :tokens api-ch)))
  (render [this]
    (component
      (let [legacy-state (:legacy/state (om-next/props this))
            subpage-com (case (:app/subpage-route (om-next/props this))
                          :notifications old-components/notifications
                          :heroku old-components/heroku-key
                          :api old-components/api-tokens
                          :plans old-components/plans
                          :beta old-components/beta-program)]
        (build-legacy
         main-template/template
         {:app legacy-state
          :crumbs [{:type :account}]
          :show-aside-menu? true
          :main-content
          (element :main-content
            (html
             [:div
              (build-legacy common/flashes (get-in legacy-state state/error-message-path))
              (build-legacy subpage-com (select-in legacy-state [state/general-message-path state/user-path state/projects-path state/web-notifications-enabled-path]))]))})))))
