(ns frontend.components.pieces.topbar
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.html :as html]
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
        [:a support-info
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
         [:li [:a {:href "/account"} "Account Settings"]]]]]])))

(dc/do
  (defcard topbar
    (html
      (topbar {:support-info {:href "#"
                              :onclick #(.log js/console "Clicked the support button!")}
               :user { :avatar_url "https://avatars.githubusercontent.com/u/5826638" }}))))
