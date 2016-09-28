(ns frontend.components.enterprise-landing
  (:require [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om]
            [frontend.components.footer :as footer])
  (:require-macros [frontend.utils :refer [html]]))

(defn enterprise-logo []
  [:figure.enterprise-logo
   [:img {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]
   [:figcaption "CircleCI Enterprise"]])

(defn home [app owner]
  (reify
    om/IDisplayName (display-name [_] "Homepage")
    om/IRender
    (render [_]
      (html
       [:div
        [:div.enterprise-landing
         [:div.jumbotron
          common/language-background-jumbotron
          [:section.container
           [:div.row
            [:article.hero-title.center-block
             [:div.text-center (enterprise-logo)]
             [:h1.text-center "Welcome to CircleCI"]]]]
          [:div.row.text-center
           [:a.btn.btn-cta.btn-success {:href (auth-url)
                                        :role "button"}
            "Get Started"]]]]
        [:footer.main-foot
         (footer/footer)]]))))
