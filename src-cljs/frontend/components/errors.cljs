(ns frontend.components.errors
  (:require [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.footer :as footer]
            [frontend.config :as config]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils :refer [auth-url]]
            [om.core :as om :include-macros true])
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
         [:div
          [:div.page.error
           [:div.jumbotron
            common/language-background-jumbotron
            [:div.banner
             [:div.container
              (cond
                (= status 500) [:img.error-img {:src (utils/cdn-path "/img/outer/errors/500.svg")
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
                               "."]
               (or (= status 404) (= status 401))
               [:p.error-message "Try heading back to our "
                [:a {:href "/"} "homepage"]
                (let [logging-in [:a {:href (auth-url)
                                      :on-click #(raise! owner [:track-external-link-clicked {:event :login-clicked
                                                                                              :path (auth-url)
                                                                                              :owner owner}])}
                                  "logging in"]]
                  (if (not logged-in?)
                    (if (config/enterprise?)
                      [:span " or " logging-in]
                      [:span ", " logging-in ", or " [:a {:href "/signup"} "signing up"]])
                    [:span " or checking out our " [:a {:href "http://blog.circleci.com/"} "blog"]]))]
               :else "Something completely unexpected happened")]]]
          [:footer.main-foot
           (footer/footer)]])))))
