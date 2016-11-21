(ns frontend.components.pieces.tabs
  (:require [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.config :as config]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn tab-row
  "A row of tabs, suitable for the top of a card.

  :tabs              - A sequence of tabs, in display order. Each tab is a map:
                       :name  - A unique identifier for the tab, not displayed to the
                                user.
                       :icon  - An icon which appears next to the tab label, often an <i>
                                element of some sort.
                       :label - The text which labels the tab. This may also be a
                                component or a list of components.
  :selected-tab-name - The name of the selected tab.
  :on-tab-click      - A handler called when a tab is clicked. The handler will receive
                       the name of the clicked tab."
  [{:keys [tabs selected-tab-name on-tab-click] :as data} owner]
  (reify
    om/IDisplayName (display-name [_] "Tab Row")

    om/IRender
    (render [_]
      (html
       [:ul {:data-component `tab-row}
        (for [{:keys [name icon label]} tabs]
          [:li (merge
                 {:key name}
                 (if (= selected-tab-name name)
                   {:class "active"}
                   {:on-click #(on-tab-click name)}))
           (when icon
             [:span.tab-icon icon])
           [:span.tab-label label]])]))))

(dc/do
  (defcard-om tab-row
    "Here, a parent renders a `tab-row`. Note that the `tab-row` itself does not
    track which tab is selected as state. Instead, the parent tells the tab row
    which tab is selected. It's the parent's responsibility to listen to the
    `:on-tab-clicked` event and track which tab should be selected, by holding
    it in its own component state, storing it in the app state (as demonstrated
    here), or some other means. (Often, in our app, we accomplish this by
    navigating to a different URL, which specifies the tab which should be
    selected.)"
    (fn [{:keys [selected-tab-name] :as data} owner]
      (om/component
        (html
         [:div
          (om/build tab-row {:tabs [{:name :tab-one
                                     :label "Tab One"}
                                    {:name :tab-two
                                     :label "Tab Two"}]
                             :selected-tab-name selected-tab-name
                             :on-tab-click #(om/update! data :selected-tab-name %)})
          "Selected: " (str selected-tab-name)])))
    {:selected-tab-name :tab-one})

  (defcard-om tab-row-with-icon
    "This `tab-row` features icons on the tab labels."
    (fn [{:keys [selected-tab-name] :as data} owner]
      (om/component
        (html
         [:div
          (om/build tab-row {:tabs [{:name :tab-one
                                     :icon (html [:i.fa.fa-linux.fa-lg])
                                     :label "Tab One"}
                                    {:name :tab-two
                                     :icon (html [:i.fa.fa-apple.fa-lg])
                                     :label "Tab Two"}]
                             :selected-tab-name selected-tab-name
                             :on-tab-click #(om/update! data :selected-tab-name %)})
          "Selected: " (str selected-tab-name)])))
    {:selected-tab-name :tab-one}))
