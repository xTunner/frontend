(ns frontend.components.errors
  (:require [frontend.state :as state]
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
            build-page? (get-in app [:navigation-data :build-page?])
            dashboard-page? (get-in app [:navigation-data :dashboard-page?])]
        (html
         [:div.page.error
          [:div.banner
           [:div.container
            [:h1 status]
            [:h3 (str (condp = status
                        401 "Login required"
                        404 "Page not found"
                        500 "Internal server error"
                        "Something unexpected happened"))]]]
          [:div.container
           (condp = status
             401 [:p
                  [:b [:a {:href (gh-utils/auth-url)
             :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                         :properties {:source "401"
                                                                                      :url js/window.location.pathname}
                                                                         :path (gh-utils/auth-url)}])}
                       "Login here"]]
                  " to view this page"]
             404 (if (and (not logged-in?) (or dashboard-page? build-page?))
                   [:div
                    [:p "We're sorry; either that page doesn't exist or you need to be logged in to view it."]
                    [:p [:b [:a {:href (gh-utils/auth-url)} "Login here"] " to view this page with your GitHub permissions."]]]
                   [:p "We're sorry, but that page doesn't exist."])
             500 [:p "We're sorry, but something broke"]
             "Something completely unexpected happened")]])))))
