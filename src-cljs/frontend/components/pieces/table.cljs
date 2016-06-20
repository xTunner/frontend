(ns frontend.components.pieces.table
  (:require [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- cell-classes
  "The HTML classes applied to a cell (th or td) in a column of the given type."
  [type]
  (let [type (if (coll? type)
               type
               #{type})]
    ;; The types are implemented as classes which are named after the types.
    ;; This is an implementation detail.
    (into []
          (comp
           (filter #{:right :shrink})
           (map name))
          type)))

(defn table
  "Our standard table component.

  :columns - A sequence of column descriptions. Each is a map with the following keys:
             :header  - The content which should appear in the header cell of the column.
             :cell-fn - A function which, given a row object, returns the content for that
                        row's cell in this column.
             :type    - A column type, or a collection of types. Available types:
                        :right  - Column aligns its content to the right. Without this
                                  type, the column will align left.
                        :shrink - Column width shrinks to fit its content. Columns without
                                  :shrink will share any leftover space.
  :rows    - A sequence of objects which will each generate a row. These will be passed to
             the columns' :cell-fns to generate each cell."
  [{:keys [columns rows]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:table {:data-component `table}
        [:thead
         [:tr
          (for [{:keys [header type]} columns]
            [:th {:class (cell-classes type)}
             header])]]
        [:tbody
         (for [row rows]
           [:tr
            (for [{:keys [cell-fn type]} columns]
              [:td {:class (cell-classes type)}
               (cell-fn row)])])]]))))

(defn action-button [on-click icon]
  (html
   [:button {:data-component `action-button
             :on-click on-click}
    icon]))
