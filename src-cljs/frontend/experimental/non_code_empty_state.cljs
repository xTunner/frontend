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
            [frontend.components.pieces.card :as card]
            [om.core :as om :include-macros true]))

(defn- empty-state-header [{:keys [name icon description]}]
  (component
    (html
      [:div
       (card/basic
         [:.header
          [:.icon-container [:.icon icon]]
          [:h1 [:b name]]
          [:p.description-text description]])])))

(defn- empty-state-demo-card [{:keys [demo-heading demo-description]}]
  (component
    (html
      [:div
       (card/basic
         [:div
          [:h4 [:b demo-heading]]
          [:div demo-description]])])))

(defn- empty-state-footer [_ owner]
  (let [track-auth-button-clicked
        (fn [vcs-type] ((om/get-shared owner :track-event)
                        {:event-type :empty-state-auth-button-clicked
                         :properties {:vcs-type vcs-type}}))]
    (reify
      om/IRender
      (render [_]
        (component
         (html
          [:div
           (card/basic
             [:div
              [:h4 [:b "Authorize CircleCI to Build"]]
              [:p "To automate your software builds and tests with CircleCI, connect to your GitHub or Bitbucket code repository."]
              [:.ctas
               (button/link {:href (gh-utils/auth-url)
                             :kind :secondary
                             :on-click #(track-auth-button-clicked :github)}
                            "Authorize GitHub")
               (button/link {:href (bitbucket/auth-url)
                             :kind :secondary
                             :on-click #(track-auth-button-clicked :bitbucket)}
                            "Authorize Bitbucket")]])]))))))

(defn full-page-empty-state
  [{:keys [content demo-heading demo-description]
    :as page-info}
   owner]
  "A full-page component shown to users who have no code identities (gh, bb).
   It contains a header with the page name, icon and a description, a footer
   with CTAs to add a code identity, some static demo content for the page, and
   an optional demo card to further explain the purpose of the page.

   :content
    The element to display as the main demo content for the page.
    Ideally a card component.

   :name
    The name of the page this empty state is standing in for.

   :icon
    An icon that represents this page.
    Should be the same icon used for this page on the nav bar.

   :description
    A more detailed description of what this page is used for.

   :demo-heading (optional)
    A heading for the optional demo card to be included below the header.
    Use the demo card to further explain the contents of the page.

   :demo-description (optional)
    A description for the optional demo card to be included below the header.
    Use the demo card to further explain the contents of the page."
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :empty-state-impression}))
    om/IRender
    (render [_]
      (component
        (html
          [:div
           (card/collection
             [(empty-state-header page-info)
              (when (or demo-heading demo-description)
                (empty-state-demo-card page-info))
              content
              (om/build empty-state-footer {})])])))))

(dc/do
  (defcard empty-state-header
           (empty-state-header {:name "Insights"
                                :icon (icon/insights)
                                :description "CircleCI insights give performance overviews of your projects and offer ideas about how to increase build speeds. Experience insights by clicking on a test project or creating one of your own."}))

  (defcard empty-state-footer
           (om/build empty-state-footer {})))
