(ns frontend.components.pieces.card
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true]
            [frontend.components.pieces.button :as button])
  (:require-macros [frontend.utils :refer [html]]))

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
    {:classname "background-gray"}))
