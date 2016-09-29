(ns frontend.components.pieces.card
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.tabs :as tabs])
  (:require-macros [frontend.utils :refer [component html]]))

(defn- exterior [content]
  (html
   [:div {:data-component `exterior}
    content]))

(defn- body [content]
  (html
   [:div {:data-component `body}
    content]))

(defn- header [title action]
  (html
   [:div {:data-component `header}
    [:.title title]
    (when action
      [:.action action])]))

(defn basic
  "The most basic of cards. The given content appears on a card."
  [content]
  (-> content body exterior))

(defn titled
  "A card with a title.

  :title - The card title.
  :action - (optional) An action to place on the right of the header."
  ([{:keys [title action]} content]
   (exterior
    (list
     (header title action)
     (body content)))))

(defn tabbed
  "A card with a tab row.

  :tab-row - The tab row to attach to the card."
  ([{:keys [tab-row]} content]
   (exterior
    (list
     tab-row
     (body content)))))

(defn collection
  "A set of cards to layout together"
  ([cards]
   (component
    (html
     [:div
      (for [card cards]
        [:.item card])]))))

(dc/do
  (defcard basic-card
    (basic "Some content.")
    {}
    {:classname "background-gray"})

  (defcard titled-card
    (titled {:title "Card Title (Generally in Title Case)"}
     "Some content.")
    {}
    {:classname "background-gray"})

  (defcard titled-card-with-action
    (titled
     {:title "Card Title (Generally in Title Case)"
      :action (button/button {:primary? true
                              :size :medium}
                             "Action")}
     "Some content.")
    {}
    {:classname "background-gray"})

  (defcard tabbed-card
    (fn [state]
      (tabbed
       {:tab-row (om/build tabs/tab-row {:tabs [{:name :tab-one
                                                 :label "Tab One"}
                                                {:name :tab-two
                                                 :label "Tab Two"}]
                                         :selected-tab-name (:selected-tab-name @state)
                                         :on-tab-click #(swap! state assoc :selected-tab-name %)})}
       "Some content."))
    {:selected-tab-name :tab-one}
    {:classname "background-gray"})

  (defcard card-collection
    (collection
     [(titled {:title "Card A"}
              "A bunch of words")
      (titled {:title "Card B"}
              "A whole bunch of words")])
    {}
    {:classname "background-gray"}))
