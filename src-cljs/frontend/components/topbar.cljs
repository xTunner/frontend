(ns frontend.components.topbar
  (:require [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.launchdarkly :as ld])
  (:require-macros [frontend.utils :refer [html]]))

(defn topbar [owner avatar-url]
  (when (ld/feature-on? "top-bar-ui-v-1")
    [:div.top-bar
     [:div.bar
      [:div.header-nav.left
       [:a.logo
        [:div.logomark
         (common/ico :logo)]]]
      [:div.header-nav.right

       ;; What's New, Notification dropdown
       [:li.top-nav-dropdown.header-nav-item.dropdown
        [:a.header-nav-link.dropdown-toggle
         {:href "#"
          :data-toggle "dropdown"
          :role "button"
          :aria-haspopup "true"
          :aria-expanded "false"}
         "What's New " [:i.material-icons "keyboard_arrow_down"]]
        [:ul.dropdown-menu
         (when-not (config/enterprise?)
           [:li [:a {:href "https://circleci.com/changelog/"} "Changelog"]])
         [:li [:a {:href "https://discuss.circleci.com/c/announcements"} "Infrastructure Announcements"]]]]

       ;; Support dropdown
       ;;
       ;; TODO -ac
       ;;
       ;; Eventually, this will be the implementation for
       ;;
       ;; [:li.top-nav-dropdown.header-nav-item.dropdown
       ;;  [:a.header-nav-link.dropdown-toggle
       ;;   {:href "#"
       ;;    :data-toggle "dropdown"
       ;;    :role "button"
       ;;    :aria-haspopup "true"
       ;;    :aria-expanded "false"}
       ;;   "Support" [:i.material-icons "keyboard_arrow_down"]]
       ;;  [:ul.dropdown-menu
       ;;   [:li [:a {:href "https://circleci.com/docs/"} "Docs"]]
       ;;   [:li [:a {:href "https://discuss.circleci.com/"} "Discuss"]]
       ;;   [:li [:a (common/contact-support-a-info owner) "Eng. Support"]]]]

       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link {:href "https://circleci.com/docs/"}
         "Docs"]]
       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link {:href "https://discuss.circleci.com/"}
         "Discuss"]]
       [:li.top-nav-dropdown.header-nav-item
        [:a.header-nav-link (common/contact-support-a-info owner)
         "Eng. Support"]]

       [:li.top-nav-dropdown.header-nav-item.dropdown
        [:a.header-nav-link.dropdown-toggle.dropbtn
         {:href "#"
          :data-toggle "dropdown"
          :role "button"
          :aria-haspopup "true"
          :aria-expanded "false"}
         [:img.gravatar {:src avatar-url}] [:i.material-icons "keyboard_arrow_down"]]
        [:ul.dropdown-menu
         [:li [:a {:href "/logout/"} "Logout"]]]]]]]))
