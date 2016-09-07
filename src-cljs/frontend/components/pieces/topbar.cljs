(ns frontend.components.pieces.topbar
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.html :as html]

            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.models.plan :as pm]

            [frontend.utils.github :as gh-utils])
  (:require-macros [frontend.utils :refer [component html]]))

(defn topbar
  "A bar which sits at the top of the screen and provides navigation and context.

  :support-info - A map defining the attributes of the support link. This is
                   most likely going to be the result of the
                   function (common/contact-support-a-info owner).

  :user         - The current user. We display their avatar in the bar."
  [{:keys [support-info user]}]
  (component
    (html
     [:div
      [:a.logomark {:href "/dashboard"
                    :aria-label "Dashboard"}
       (common/ico :logo)]
      [:div.navs-container
        [:ul.nav-items.collapsing-nav
          [:li
         [:a {:href "/dashboard"
                   :aria-label "Builds"}
          [:i.material-icons "storage"] "Builds"]]
          [:li
         [:a {:href "/build-insights"
                   :aria-label "Insights"}
          [:i.material-icons "assessment"] "Insights"]]
          [:li
         [:a {:href "/projects"
                   :aria-label "Projects"}
          [:i.material-icons "book"] "Projects"]]
          [:li
         [:a {:href "/team"
                   :aria-label "Teams"}
          [:i.material-icons "group"] "Teams"]]]
        [:ul.nav-items.collapsing-nav
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
         [:li
          [:a (html/open-ext {:href "https://circleci.com/docs/"})
           "Docs"]]
         [:li
          [:a (html/open-ext {:href "https://discuss.circleci.com/"})
           "Discuss"]]
         [:li
          [:a support-info
           "Support"]]]
        [:ul.nav-items
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
        [:button.navbar-toggler.hidden-sm-up {:type "button"
                                                      :data-toggle "collapse"
                                                      :data-target "collapsing-nav"
                                                      :aria-controls "collapsing-nav"
                                                      :aria-expanded "false"
                                                      :aria-label "Toggle navigation"}
           [:i.material-icons "menu"]]]])))

(dc/do
  (defcard topbar
    (html
      (topbar {:support-info {:href "#"
                              :onclick #(.log js/console "Clicked the support button!")}
               :user { :avatar_url "https://avatars.githubusercontent.com/u/5826638" }}))))
