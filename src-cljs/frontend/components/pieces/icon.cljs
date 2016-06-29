(ns frontend.components.pieces.icon
  (:require-macros [frontend.utils :refer [html]]))

(defn delete
  "Used to delete things, most notably on action buttons in table rows."
  []
  (html [:i.material-icons "cancel"]))
