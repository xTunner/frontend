(ns frontend.components.pieces.topbar
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.html :as html]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om])
  (:require-macros [frontend.utils :refer [component html]]))

(defn topbar
  "A bar which sits at the top of the screen and provides navigation and context.

  :user - The current user. We display their avatar in the bar."
  [{:keys [user]} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html
          [:div
           [:div.navs-container
            [:a.logomark.hidden-sm-down {:href "/dashboard"
                          :aria-label "Dashboard"}
             (common/ico :logo)]
            [:a.logomark-narrow.hidden-md-up {:href "/dashboard"
                          :aria-label "Dashboard"}
             [:img {:src (utils/cdn-path "/img/inner/icons/Logo-Wordmark.svg")}]]
            [:ul.nav-items.collapsing-nav
              [:li
                [:a.has-icon {:href "/dashboard"
                              :aria-label "Builds"}
                  [:i.material-icons "storage"] "Builds"]]
              [:li
                [:a.has-icon {:href "/build-insights"
                              :aria-label "Insights"}
                  [:i.material-icons "assessment"] "Insights"]]
              [:li
                [:a.has-icon {:href "/projects"
                              :aria-label "Projects"}
                  [:i.material-icons "book"] "Projects"]]
              [:li
                [:a.has-icon {:href "/team"
                              :aria-label "Teams"}
                  [:i.material-icons "group"] "Teams"]]]
             [:ul.nav-items.nav-items-left.collapsing-nav
              [:li
                [:a.has-icon {:href "/build-insights"
                              :aria-label "System Status"}
                  [:i.material-icons.status.operational "grain"] "System Status"]] ;; blur_on leak_add network_check
              [:li.dropdown
               [:button.dropdown-toggle
                {:data-toggle "dropdown"
                 :aria-haspopup "true"
                 :aria-expanded "false"}
                "What's New " [:i.material-icons "keyboard_arrow_down"]]
               [:ul.dropdown-menu.pull-right.animated.slideInDown
                (when-not (config/enterprise?)
                  [:li [:a (html/open-ext {:href "https://circleci.com/changelog/"}) "Changelog"]])
                  [:li [:a (html/open-ext {:href "https://discuss.circleci.com/c/announcements"}) "Announcements"]]]]
              [:li.dropdown
               [:button.dropdown-toggle
                {:data-toggle "dropdown"
                 :aria-haspopup "true"
                 :aria-expanded "false"}
                "Get Help " [:i.material-icons "keyboard_arrow_down"]]
               [:ul.dropdown-menu.pull-right.animated.slideInDown
                [:li
                 [:a (html/open-ext {:href "https://circleci.com/docs/"})
                  "Docs"]]
                [:li
                 [:a (html/open-ext {:href "https://discuss.circleci.com/"})
                  "Discuss"]]
                [:li
                 ;; FIXME: NEED TO ADD THIS AGAIN IN THE DEFN
                 [:a.support-info
                  "Support"]]]]

              [:li.dropdown.user-menu
               [:button.dropdown-toggle
                {:data-toggle "dropdown"
                 :aria-haspopup "true"
                 :aria-expanded "false"}
                [:img.gravatar {:src (gh-utils/make-avatar-url user :size 60)}]
                [:i.material-icons "keyboard_arrow_down"]]
               [:ul.dropdown-menu.pull-right.animated.slideInDown
                [:li [:a {:href "/logout/"} "Logout"]]
                [:li [:a {:href "/account"} "User Settings"]]
                (when (:admin user)
                  [:li [:a {:href "/admin"} "Admin"]])]]]

             [:ul.nav-items.nav-items-left.hidden-md-up
              [:li
                [:a.has-icon {:href "/build-insights"
                              :aria-label "Status"}
                  [:i.material-icons.status.operational "grain"]]]] ;; blur_on leak_add network_check
             [:button.navbar-toggler.hidden-md-up {:type "button"
                                                           :data-toggle "collapse"
                                                           :data-target "collapsing-nav"
                                                           :aria-controls "collapsing-nav"
                                                           :aria-expanded "false"
                                                           :aria-label "Toggle navigation"}
                [:i.material-icons "menu"]]]])))))

(dc/do
  (defcard topbar
    (om/build topbar {:user {:avatar_url "https://avatars.githubusercontent.com/u/5826638"}})))
