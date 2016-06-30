(ns frontend.components.pieces.card
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- exterior [content]
  (html
   [:div {:data-component `exterior}
    content]))

(defn- body [content]
  (html
   [:div {:data-component `body}
    content]))

(defn- header [title]
  (html
   [:div {:data-component `header}
    [:.title title]]))


(defn basic
  "The most basic of cards. The given content appears on a card."
  [content]
  (-> content body exterior))

(defn titled
  "A card with a title."
  [title content]
  (exterior
   (list
    (header title)
    (body content))))


(dc/do
  (defcard basic-card
    (basic "Some content.")
    {}
    {:classname "background-gray"})

  (defcard titled-card
    (titled
     "Card Title (Generally in Title Case)"
     "Some content.")
    {}
    {:classname "background-gray"}))
