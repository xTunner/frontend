(ns frontend.components.navbar
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.env :as env]
            [frontend.utils :as utils]
            [frontend.utils.github :refer [auth-url]]
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

(defn show-environment? [user]
  (:admin user))

(defn crumb [data]
  (let [attrs {:href (:path data)
             :title (:name data)}]
    (if (:active data)
      [:span attrs (:name data)]
      [:a attrs (:name data)])))

(defn logged-out-header [{:keys [flash]}]
  [:div
   [:div
    (when flash
      [:div#flash flash])]
   [:div#navbar
    [:div.container
     [:div.row
      [:div.span8
       [:div.row
        [:a#logo.span2
         {:href "/"}
         [:img
          {:width "130",
           :src (utils/asset-path "/img/logo-new.svg")
           :height "40"}]]
        [:nav.span6
         {:role "navigation"}
         [:ul.nav.nav-pills
          [:li [:a {:href "/about"} "About"]]
          [:li [:a {:href "/pricing"} "Pricing"]]
          [:li [:a {:href "/docs"} "Documentation"]]
          [:li [:a {:href "/jobs"} "Jobs"]]
          [:li [:a {:href "http://blog.circleci.com"} "Blog"]]]]]]
      [:div.controls.span4
       ;; XXX: mixpanel event tracking
       [:a#login.login-link {:href (auth-url)
                             :title "Sign in with Github"
                             :data-bind "track_link: {event: 'Auth GitHub', properties: {'source': 'header sign-in', 'url' : window.location.pathname}}"}
        "Sign in"]
       [:span.seperator "|"]
       [:a#login.login-link {:href (auth-url)
                             :title "Sign up with Github"
                             :data-bind "track_link: {event: 'Auth GitHub', properties: {'source': 'header sign-up', 'url' : window.location.pathname}}"}
        "Sign up "
        [:i.fa.fa-github-alt]]]]]]])

(defn logged-in-header [{:keys [user settings crumbs controls-ch]}]
  [:nav.header-nav
   [:div.header-nav-logo [:a {:href "/"}]]
   [:div.header-nav-breadcrumb
    [:nav
     (map crumb crumbs)]]
   (when (show-environment? user)
     [:div.header-nav-environment
      [:span {:class (str "env-" (name (env/env)))}
       (name (env/env))]])
   [:div.header-nav-docs
    [:a.menu-item
     {:target "_blank", :href "/docs"}
     [:i.fa.fa-files-o]
     [:span "Documentation"]]]
   [:div.header-nav-menu {:class (when (get-in settings [:menus :user :open]) "open")}
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
         "Find project on Intercom"]))]]]  )

(defn navbar [app owner opts]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (get-in opts [:comms :controls])
            user (:current-user app)]
        (html/html
         (if user
           (logged-in-header {:user user
                              :settings (:settings app)
                              :crumbs (:crumbs app)
                              :controls-ch controls-ch})
           (logged-out-header {:flash (get-in app [:render-context :flash])})))))))
