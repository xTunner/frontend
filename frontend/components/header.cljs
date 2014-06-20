(ns frontend.components.header
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.forms :as forms]
            [frontend.components.instrumentation :as instrumentation]
            [frontend.env :as env]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn show-follow-project-button? [app]
  (when-let [project (get-in app state/project-path)]
    (and (not (:followed project))
         (= (vcs-url/org-name (:vcs_url project))
            (get-in app [:navigation-data :org]))
         (= (vcs-url/repo-name (:vcs_url project))
            (get-in app [:navigation-data :repo])))))

(defn settings-link [app]
  (when (get-in app state/show-nav-settings-link-path)
    (let [navigation-data (:navigation-data app)]
      (cond (:repo navigation-data) [:a.settings {:href (routes/v1-project-settings navigation-data)
                                                  ;; XXX implement tooltips
                                                  :tooltip "Project Settings"}
                                     (common/ico :settings-light)]
            (:org navigation-data) [:a.settings {:href (routes/v1-org-settings navigation-data)
                                                 :tooltip "Org Settings"}
                                    (common/ico :settings-light)]
            :else nil))))

(defn head-user [app owner]
  (reify
    om/IRender
    (render [_]
      (let [crumbs-data (get-in app state/crumbs-path)
            project (get-in app state/project-path)
            project-id (project-model/id project)
            vcs-url (:vcs_url project)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html
          [:div.head-user
           [:div.breadcrumbs
            (when (seq crumbs-data)
              [:a {:title "home", :href "/"} [:i.fa.fa-home] " "])
            (crumbs/crumbs crumbs-data)]
           (when (show-follow-project-button? app)
             (forms/stateful-button
               [:button#follow-project-button
                {:on-click #(put! controls-ch [:followed-project {:vcs-url vcs-url :project-id project-id}])
                 :data-spinner true}
                "follow the " (vcs-url/repo-name vcs-url) " project"]))
           (settings-link app)])))))

(defn head-admin [app owner]
  (reify
    om/IRender
    (render [_]
      (let [open? (get-in app state/show-admin-panel-path)
            expanded? (get-in app state/show-instrumentation-line-items-path)
            controls-ch (om/get-shared owner [:comms :controls])
            user-session-settings (get-in app [:render-context :user_session_settings])]
        (html
         [:div.head-admin {:class (utils/inspect (concat (when open? ["open"])
                                                         (when expanded? ["expanded"])))}
          [:div.admin-tools
           [:div.environment {:class (str "env-" (name (env/env)))
                              :role "button"
                              :on-click #(put! controls-ch [:show-admin-panel-toggled])}
            (name (env/env))]

           (om/build instrumentation/summary (:instrumentation app))

           [:div.options
            [:a {:href "/admin"} "admin"]
            [:a {:href "/admin/users"} "users"]
            [:a {:href "/admin/recent-builds"} "recent"]
            [:a {:href "/admin/projects"} "projects"]
            (let [use-local-assets (get user-session-settings :use_local_assets)]
              [:a {:on-click #(put! controls-ch [:set-user-session-setting {:setting :use-local-assets
                                                                            :value (not use-local-assets)}])}
               "local assets " (if use-local-assets "off" "on")])
            [:a {:on-click #(put! controls-ch [:set-user-session-setting {:setting :use-om
                                                                          :value false}])}
             "use ko"]
            (let [current-build-id (get user-session-settings :om_build_id "dev")]
              (for [build-id (remove (partial = current-build-id) ["dev" "whitespace" "production"])]
                [:a.menu-item
                 {:on-click #(put! controls-ch [:set-user-session-setting {:setting :om-build-id
                                                                           :value build-id}])}
                 [:span (str build-id " compiler")]]))
            [:a {:on-click #(put! controls-ch [:clear-instrumentation-data-clicked])} "clear stats"]]]
          (when (and open? expanded?)
            (om/build instrumentation/line-items (:instrumentation app)))])))))

(defn inner-header [app owner]
  (reify
    om/IRender
    (render [_]
      (let [admin? (get-in app [:current-user :admin])]
        (html
         [:header.main-head
          (when admin?
            (om/build head-admin app))
          (when (seq (get-in app state/crumbs-path))
            (om/build head-user app))])))))

(defn outer-header [app owner]
  (reify
    om/IRender
    (render [_]
      (html [:div "so we know its outer header"]))))

(defn header [app owner]
  (reify
    om/IRender
    (render [_]
      (let [inner? (get-in app state/inner?-path)]
        (html
          (if inner?
            (om/build inner-header app)
            (om/build outer-header app)))))))
