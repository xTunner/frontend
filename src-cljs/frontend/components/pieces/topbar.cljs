(ns frontend.components.pieces.topbar
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.html :as html]
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
          [:a.logomark {:href "/dashboard"
                        :aria-label "Dashboard"}
           (common/ico :logo)]

          [:ul.nav-items
           [:li.dropdown
            [:button.dropdown-toggle
             {:data-toggle "dropdown"
              :aria-haspopup "true"
              :aria-expanded "false"}
             "What's New " [:i.material-icons "keyboard_arrow_down"]]
            [:ul.dropdown-menu.pull-right
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
            [:a (common/contact-support-a-info owner)
             "Support"]]
           [:li.dropdown.user-menu
            [:button.dropdown-toggle
             {:data-toggle "dropdown"
              :aria-haspopup "true"
              :aria-expanded "false"}
             [:img.gravatar {:src (gh-utils/make-avatar-url user :size 60)}]
             [:i.material-icons "keyboard_arrow_down"]]
            [:ul.dropdown-menu.pull-right
             [:li [:a {:href "/logout/"} "Logout"]]
             [:li [:a {:href "/account"} "User Settings"]]]]]])))))

(dc/do
  (defcard topbar
    (om/build topbar {:user {:avatar_url "https://avatars.githubusercontent.com/u/5826638"}})))
