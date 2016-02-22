(ns frontend.components.errors
  (:require [frontend.state :as state]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true]
            [frontend.utils.github :refer  [auth-url]])
  (:require-macros [frontend.utils :refer [html]]))

(defn error-page [app owner]
  (reify
    om/IRender
    (render [_]
      (let [status (get-in app [:navigation-data :status])
            logged-in? (get-in app state/user-path)
            orig-nav-point (get-in app [:original-navigation-point])
            _ (utils/mlog "error-page render with orig-nav-point " orig-nav-point " and logged-in? " (boolean logged-in?))]
        (html
          [:div.page.error
           [:div.jumbotron
            common/language-background-jumbotron
            [:div.banner
             [:div.container
              (cond
                (= status 500) [:img.error-img {:src  (utils/cdn-path "/img/outer/errors/500.svg")
                                                :alt "500"}]
                (or (= status 404) (= status 401)) [:img.error-img {:src (utils/cdn-path "/img/outer/errors/404.svg")
                                                                    :alt "404"}]
                :else [:span.error-zero])]]
            [:div.container
             [:p "Something doesn't look right ..."]
             (cond
               (= status 500) [:p.error-message "If the problem persists, feel free to check out our "
                               [:a {:href "http://status.circleci.com/"} "status"]
                               " or "
                               [:a {:href "mailto:sayhi@circleci.com"} "contact us"]
                               "." ]
               (or (= status 404) (= status 401))
               [:p.error-message "Try heading back to our "
                [:a {:href "/"} "homepage"]
               (if (not logged-in?)
                  [:span ", " [:a {:href (auth-url)
                              :on-click #(raise! owner [:track-external-link-clicked {:event :login-clicked
                                                                                      :owner owner}])}
                          "logging in"]
                   ", or "
                   [:a {:href "/signup"} "signing up"]]
                  '(" or checking out our " [:a {:href "http://blog.circleci.com/"} "blog"]))]
               :else "Something completely unexpected happened")]]])))))
