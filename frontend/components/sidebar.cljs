(ns frontend.components.sidebar
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.utils :as utils]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn build [project build]
  [:a
   {:href (build-model/path-for project build)
    :class (:status build)}
   [:i.fa
    {:class (build-model/status-icon build)
     ;; XXX Build tooltip component
     :data-bind
     "tooltip: {title: tooltip_title, animation: false}"}]])

(defn branch [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [project (:project data)
            [name-kw branch-builds] (:branch data)
            display-builds (take 5 (sort-by :build_num (concat (:running_builds branch-builds)
                                                               (:recent_builds branch-builds))))]
        (html [:div.aside-project-branch
               [:a.aside-name
                {:title (str "View " (name name-kw) " builds only"),
                 :href (project-model/path-for project (name name-kw))}
                (utils/trim-middle (name name-kw) 23)]
               [:div.aside-status-icons
                (map (partial build project) display-builds)]])))))

(defn project [data owner opts]
  (reify
    om/IRender
    (render [_]
      (html
       (let [user     (:user data)
             project  (:project data)
             ch       (:ch data)
             settings (:settings data)
             project-id (project-model/id project)
             personal-branches (project-model/personal-branches user project)]
         [:div.aside-project
          [:div.aside-project-info
           [:a.aside-name
            {:title (str "View builds for entire " (project-model/project-name project) " project"),
             :href (project-model/path-for project)}
            (utils/trim-middle (project-model/project-name project) 23)]
           [:a.settings
            {:href (project-model/settings-path project)}
            [:i.fa.fa-cog
             ;; XXX Add tooltip component here
             {:data-original-title "",
              :data-bind
              "tooltip: {title: 'Project settings', animation: false}"}]]
           (when-not (= (count personal-branches) (count (:branches project)))
             [:a.toggle-all
              {:role "button",
               :class (when (get-in settings [:projects project-id :show-all-branches]) "active")
               :on-click #(put! ch [:show-all-branches-toggled project-id])}
              [:i.fa.fa-caret-down
               {:data-original-title "",
                ;; XXX Make tooltip component
                :data-bind
                "tooltip: {title: show_all_tooltip, animation: false}"}]])]
          (map #(om/build branch
                          {:project project
                           :branch %}
                          {:react-key (first %)})
               (sort-by (comp str first)
                        (if (get-in settings [:projects project-id :show-all-branches])
                          (:branches project)
                          personal-branches)))])))))

(defn sidebar [app owner opts]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (get-in opts [:comms :controls])
            user (:current-user app)]
        (html/html
         [:aside
          [:div.aside-actions
           ;; XXX loading spinner
           [:a#add-projects
            {:href "/add-projects"}
            [:i.fa.fa-plus]
            [:span "Projects"]]
           [:a#invite
            ;; XXX Handle opening invite form
            {:on-click #(put! controls-ch [:invite-form-opened])}
            [:i.fa.fa-plus]
            [:span "Collaborator"]]
           ;; XXX Show follow project button
           ;; XXX Projects have not been loaded
          [:div.aside-projects
           (map #(om/build project
                           {:project %
                            :user user
                            :ch controls-ch
                            :settings (:settings app)})
                (:projects app))]]])))))
