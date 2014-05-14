(ns frontend.components.navbar
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

;; XXX: Replace logic to handle Gravatar/GitHub fallback
(defn gravatar-url [user]
  [:img
   {:height "30",
    :width "30",
    :src
    "https://secure.gravatar.com/avatar/c0ca7580659419b084d64cc3c3e8d83c?s=30&d=https%3A%2F%2Fidenticons.github.com%2F47e9e116b0f57e67755afde0fce6e5d3.png",}])

(defn from-heroku? [app]
  (get-in app [:render-context :from_heroku]))

(defn show-environment? [user]
  (:admin user))

(defn crumb [data]
  (let [attrs {:href (:path data)
             :title (:name data)}]
    (if (:active data)
      [:span attrs (:name data)]
      [:a attrs (:name data)])))

(defn navbar [app owner opts]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (get-in opts [:comms :controls])
            user (:current-user app)]
        (html/html
         [:nav.header-nav
          [:div.header-nav-logo [:a {:href "/"}]]
          [:div.header-nav-breadcrumb
           [:nav
            (map crumb (:crumbs app))]]
          (when (show-environment? user)
            [:div.header-nav-environment
             [:span {:class (str "env-" (:environment app))}
              (:environment app)]])
          [:div.header-nav-docs
           [:a.menu-item
            {:target "_blank", :href "/docs"}
            [:i.fa.fa-files-o]
            [:span "Documentation"]]]
          [:div.header-nav-menu {:class (when (get-in app [:settings :menus :user :open]) "open")}
           [:a
            {:on-click #(put! controls-ch [:user-menu-toggled])}
            (if-let [avatar (gravatar-url user)]
              avatar
              [:span (:login user)])
            [:i.fa.fa-caret-down]]
           [:aside
            [:a.menu-item
             {:href "/account"}
             [:i.fa.fa-gears]
             [:span "Settings"]]
            (when-not (from-heroku? app)
              [:a#logout-link.menu-item
               {:href "/logout"}
               [:i.fa.fa-sign-out]
               [:span "Logout"]])
            [:a.menu-item
             {:on-click #(put! controls-ch [:intercom-dialog-raised])}
             [:i.fa.fa-bullhorn]
             [:span "Support"]]
            [:a.menu-item
             {:target "_blank", :href "https://www.hipchat.com/gjwkHcrD5"}
             [:i.fa.fa-comments]
             [:span "Chat support"]]
            (if (:admin user)
              (list
               [:a.menu-item {:href "/admin"} "Admin"]
               [:a.menu-item {:href "/admin/users"} "Users"]
               [:a.menu-item {:href "/admin/recent-builds"} "Recent builds"]
               [:a.menu-item {:href "/admin/projects"} "Projects"]
               [:a.menu-item
                {:on-click #(put! controls-ch [:intercom-user-inspected])}
                "Find project on Intercom"]))]]])))))
