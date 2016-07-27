(ns frontend.components.topbar
  (:require [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.launchdarkly :as ld])
  (:require-macros [frontend.utils :refer [html]]))

(defn topbar [owner avatar-url]
  (when (ld/feature-on? "top-bar-ui-v-1")
    [:div.top-bar
     [:div.bar
      [:ul.header-nav.left
       [:a.logo
        [:div.logomark
         (common/ico :logo)]]]
      [:ul.header-nav.right

       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link {:href "https://circleci.com/docs/"}
         [:i.material-icons "description"]
         " Docs"]]
       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link {:href "https://discuss.circleci.com/"}
         [:i.material-icons "people"]
         " Community Help"]]
       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link (common/contact-support-a-info owner)
         [:i.material-icons "chat"]
         " Eng. Support"]]

       (when-not (config/enterprise?)
         [:li.top-nav-dropdown.header-nav-item
          [:a.header-nav-link {:href "https://circleci.com/changelog/"}
           [:i.material-icons "receipt"]
           " Changelog"]])

       ;; TODO -ac need to be consistent between header/nav/top-bar lol
       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link {:href "https://discuss.circleci.com/c/announcements"}
         [:i.material-icons "developer_board"]
         " Infrastructure Announcements"]]

       [:li.top-nav-dropdown.header-nav-item
        [:a.dropbtn.header-nav-link
         [:img.gravatar {:src avatar-url}]]
        [:div.dropdown-content
         [:a {:href "/logout/"}
          [:i.material-icons "power_settings_new"]
          " Logout"]]]]]]))
