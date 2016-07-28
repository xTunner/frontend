(ns frontend.components.pieces.topbar
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.common :as common]
            [frontend.config :as config]
            [frontend.utils.github :as gh-utils])
  (:require-macros [frontend.utils :refer [component html]]))

(defn topbar
  ":support-info      This is probably the output of the function
                         (common/contact-support-a-info owner)
   :user              We "
  [{:keys [support-info user]}]
  (let [avatar-url (gh-utils/make-avatar-url user :size 60)]
    (component
    (html
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
          [:a.header-nav-link support-info
           "Eng. Support"]]

         [:li.top-nav-dropdown.header-nav-item.dropdown
          [:a.header-nav-link.dropdown-toggle.dropbtn
           {:href "#"
            :data-toggle "dropdown"
            :role "button"
            :aria-haspopup "true"
            :aria-expanded "false"}
           (.log js/console
                 ;; "Support-info is: " (str (seq (common/contact-support-a-info owner)))
                 ", Avatar URL is: "  avatar-url
                 ", but user is: " (str (seq  user)))
           [:img.gravatar {:src avatar-url}] [:i.material-icons "keyboard_arrow_down"]]
          [:ul.dropdown-menu
           [:li [:a {:href "/logout/"} "Logout"]]]]]]]))))

(dc/do
  (defcard topbar
    (html
      (topbar {:support-info {:href "#"
                              :onclick #(.log js/console "Clicked the support button!")}
               :user { :avatar_url "https://avatars.githubusercontent.com/u/5826638" }}))))
