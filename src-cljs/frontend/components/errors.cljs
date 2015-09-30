(ns frontend.components.errors
  (:require [frontend.state :as state]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn error-page [app owner]
  (reify
    om/IRender
    (render [_]
      (let [status (get-in app [:navigation-data :status])
            logged-in? (get-in app state/user-path)
            orig-nav-point (get-in app [:original-navigation-point])
            _ (utils/mlog "error-page render with orig-nav-point " orig-nav-point " and logged-in? " (boolean logged-in?))
            maybe-login-page? (some #{orig-nav-point} [:dashboard :build])]
        (html
          [:div.page.error
           [:div.jumbotron
            common/language-background-jumbotron
            [:div.banner
             [:div.container
              (condp = status 
                401 [:h1 4 [:span.error-zero] 1]
                404 [:h1 4 [:span.error-zero] 4]
                500 [:h1 5 [:span.error-zero] 0]
                [:span.error-zero])]]
            [:div.container
             [:p "Something doesn't look right ..."]
             (cond
               (= status 401) [:p.error-message "You'll need to "
                    [:a {:href (gh-utils/auth-url)
                         :on-click #(raise! owner [:track-external-link-clicked
                                                   {:event "login_click"
                                                    :properties {:source "401"
                                                                 :url js/window.location.pathname}
                                                    :path (gh-utils/auth-url)}])}
                     "login"]
                    " or " 
                    [:a {:href "/signup"
                         :on-mouse-up #(analytics/track-signup-click)}
                     " signup" ] ]
               (some #{status} '(404 500)) [:p.error-message "Try heading back to our "
                          [:a {:href "/"} "homepage"]
                          " or checking out our "
                          [:a {:href "http://blog.circleci.com/"} "blog"]]
               :else "Something completely unexpected happened"
               )]]])))))
