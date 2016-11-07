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
             [:li.dropdown
              [:button.dropdown-toggle
               {:data-toggle "dropdown"
                :aria-haspopup "true"
                :aria-expanded "false"}
               "Updates" [:i.material-icons "keyboard_arrow_down"]]
              [:ul.dropdown-menu.pull-right.animated.slideInDown
               (when-not (config/enterprise?)
                 [:li [:a (html/open-ext {:href "https://circleci.com/changelog/"}) "Changelog"
                       [:span.description "The latest CircleCI product updates."]]])
               [:li [:a (html/open-ext {:href "https://discuss.circleci.com/c/announcements"}) "Announcements"
                     [:span.description "Important announcements from CircleCI about upcoming features and updates."]]]]]
             [:li.dropdown
              [:button.dropdown-toggle
               {:data-toggle "dropdown"
                :aria-haspopup "true"
                :aria-expanded "false"}
               "Support" [:i.material-icons "keyboard_arrow_down"]]
              [:ul.dropdown-menu.pull-right.animated.slideInDown
               [:li
                [:a (html/open-ext {:href "https://circleci.com/docs/"})
                 "Docs"
                 [:span.description "Read about building, testing, and deploying your software on CircleCI."]]]
               [:li
                [:a (html/open-ext {:href "https://discuss.circleci.com/"})
                 "Discuss"
                 [:span.description "Get help and meet developers talking about testing, continuous integration, and continuous delivery."]]]
               [:li
                 ;; FIXME: NEED TO ADD LINK TO ELEV.IO SUPPORT WIDGET
                [:a.support-info {:href "/support"}
                 "Support"
                 [:span.description "Send us a message. We are here to help!"]]]
               [:li
                [:a (html/open-ext {:href "https://discuss.circleci.com/c/feature-request"})
                 "Suggest a feature"]]
               [:li
                [:a (html/open-ext {:href "https://discuss.circleci.com/c/bug-reports"})
                 "Report an issue"]]
               [:li
                   ;; FIXME: NEED TO ADD QUICK START PAGE
                  [:a {:href "/quick-start"}
                   "Quick start"]]
               [:li
                 [:a.has-icon {:href "/build-insights"
                               :aria-label "System Status"}
                  "System status" [:i.material-icons.status.operational "grain"]]]]] ;; blur_on leak_add network_check


             [:li.dropdown.user-menu
              [:button.dropdown-toggle
               {:data-toggle "dropdown"
                :aria-haspopup "true"
                :aria-expanded "false"}
               [:img.gravatar {:src (gh-utils/make-avatar-url user :size 60)}]]
              [:ul.dropdown-menu.pull-right.animated.slideInDown
               [:li [:a {:href "/account"} "User settings"]]
               (when (:admin user)
                 [:li [:a {:href "/admin"} "Admin"]])
               [:li [:a {:href "/logout/"} "Logout"]]]]]

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
