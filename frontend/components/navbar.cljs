(ns frontend.components.navbar
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.env :as env]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

;; XXX: Replace logic to handle Gravatar/GitHub fallback
(defn gravatar-url [user]
  [:img
   {:height "30"
    :width "30"
    :src (-> user :selected_email utils/email->gravatar-url)}])

(defn show-environment? [user]
  (:admin user))

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

(defn logged-in-header [{:keys [user settings crumbs controls-ch user-session-settings]}]
  [:nav.header-nav
   [:div.header-nav-logo [:a {:href "/"}]]
   [:div.header-nav-breadcrumb
    (om/build crumbs/crumbs crumbs)]
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
        [:a.menu-item {:href "/admin"}
         [:i.fa.fa-wrench]
         "Admin"]
        [:a.menu-item {:href "/admin/users"}
         [:i.fa.fa-group]
         "Users"]
        [:a.menu-item {:href "/admin/recent-builds"}
         [:i.fa.fa-clock-o]
         "Recent builds"]
        [:a.menu-item {:href "/admin/projects"}
         [:i.fa.fa-file-text]
         "Projects"]
        [:a.menu-item
         {:on-click #(put! controls-ch [:intercom-user-inspected])}
         [:i.fa.fa-search]
         "Find project on Intercom"]
        (let [use-local-assets (get user-session-settings :use_local_assets)]
          [:a.menu-item
           {:on-click #(put! controls-ch [:set-user-session-setting {:setting :use-local-assets
                                                                     :value (not use-local-assets)}])}
           [:i.fa.fa-home]
           (if use-local-assets "Stop using local assets" "Use local assets")])
        [:a.menu-item
         {:on-click #(put! controls-ch [:set-user-session-setting {:setting :use-om
                                                                    :value false}])}
         [:i.fa.fa-coffee]
         "Stop using om"]
        (let [current-build-id (get user-session-settings :om_build_id "dev")]
          (for [build-id (remove (partial = current-build-id) ["dev" "whitespace" "production"])]
            [:a.menu-item
             {:on-click #(put! controls-ch [:set-user-session-setting {:setting :om-build-id
                                                                       :value build-id}])}
             [:span (str "Use " build-id " om compiler")]]))))]]])

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
                              :user-session-settings (get-in app [:render-context :user_session_settings])
                              :crumbs (get-in app state/crumbs-path)
                              :controls-ch controls-ch})
           (logged-out-header {:flash (get-in app [:render-context :flash])})))))))
