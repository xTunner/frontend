(ns frontend.components.header
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.forms :as forms]
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
         (= (vcs-url/project-name (:vcs_url project))
            (get-in app [:project-settings-project-name])))))

(defn settings-link [app]
  (let [navigation-data (:navigation-data app)]
    (cond (:repo navigation-data) [:a.settings {:href (routes/v1-project-settings navigation-data)
                                                ;; XXX implement tooltips
                                                :tooltip "Project Settings"}
                                   (common/ico :settings-light)]
          (:org navigation-data) [:a.settings {:href (routes/v1-org-settings navigation-data)
                                               :tooltip "Org Settings"}
                                  (common/ico :settings-light)]
          :else nil)))

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

(defn inner-header [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:header.main-head
        ;; XXX add head-admin
        (when (seq (get-in app state/crumbs-path))
          (om/build head-user app))]))))

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
