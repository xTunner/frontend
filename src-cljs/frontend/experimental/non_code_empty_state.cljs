(ns frontend.experimental.non-code-empty-state
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.async :refer [raise!]]
            [frontend.components.add-projects :refer [orgs-from-repos]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.utils :as utils :refer-macros [component html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.bitbucket :as bitbucket]
            [frontend.components.pieces.card :as card]))

(defn- empty-state-header [{:keys [name icon subheading]}]
  (component
    (html
      [:div
       (card/basic
         [:.header
          [:.icon-container [:.icon icon]]
          [:h1 [:b name]]
          [:p.subheading-text subheading]])])))

(defn- empty-state-footer []
  (component
    (html
      [:div
       (card/basic
         [:div
          [:h4 [:b "Authorize CircleCI to Build"]]
          [:p "To automate your software builds and tests with CircleCI, connect to your GitHub or Bitbucket code repository."]
          [:.ctas
           (button/link {:href (gh-utils/auth-url)
                         :kind :secondary}
                        "Authorize GitHub")
           (button/link {:href (bitbucket/auth-url)
                         :kind :secondary}
                        "Authorize Bitbucket")]])])))

(defn- demo-warn-card [{:keys [demo-heading demo-description]}]
  (component
    (html
      [:div
       (card/basic
         [:div
          [:h4 [:b demo-heading]]
          [:div demo-description]])])))

(defn empty-state-main-page [page-info children]
  (component
    (html
      [:div
       (card/collection
         [(empty-state-header page-info)
          (demo-warn-card page-info)
          children
          (empty-state-footer)])])))

(dc/do
  (defcard empty-state-header
           (empty-state-header {:name "Insights"
                                :icon (icon/insights)
                                :subheading "CircleCI insights give performance overviews of your projects and offer ideas about how to increase build speeds. Experience insights by clicking on a test project or creating one of your own."}))

  (defcard empty-state-footer
           (empty-state-footer {:name "Insights"
                                :footer-heading "Use insights on your projects"
                                :footer-description "To use insights on your projects, you need to add your code and run a build"})))
