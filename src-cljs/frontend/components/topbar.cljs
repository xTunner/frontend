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
       ;; TODO -ac Hmmm, v2, include the icons? [:i.material-icons "receipt"]
       [:li.top-nav-dropdown.header-nav-item
        [:a.dropbtn.header-nav-link "Support"]
        [:div.dropdown-content
         [:a {:href "https://circleci.com/docs/"}
          "Self Help"]
         [:a {:href "https://discuss.circleci.com/"}
          "Community Help"]
         [:a (common/contact-support-a-info owner)
          "Eng. Support"]]]

       [:li.top-nav-dropdown.header-nav-item
        [:a.dropbtn.header-nav-link "What's New"]
        [:div.dropdown-content
         (when-not (config/enterprise?)
           [:a {:href "https://circleci.com/changelog/"}
            "Changelog"])
         [:a {:href "https://discuss.circleci.com/c/announcements"}
          "Infrastructure Announcements"]]]

       [:li.top-nav-dropdown.header-nav-item
        [:a.dropbtn.header-nav-link
         [:img.gravatar {:src avatar-url}]
         ;; TODO -ac Ask Jared if this should be a hover or press?
         #_[:i.material-icons "keyboard_arrow_down"]]
        [:div.dropdown-content
         [:a {:href "/logout/"}
          "Logout"]]]]]]))
