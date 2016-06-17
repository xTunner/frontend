(ns frontend.components.pieces.table
  (:require [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn table
  "Our standard table component.

  :columns - A sequence of column descriptions. Each is a map with the following keys:
             :header  - The content which should appear in the header cell of the column.
             :cell-fn - A function which, given a row object, returns the content for that
                        row's cell in this column.
             :type    - A column type. Currently, the only defined type is :right, which
                        will produce a column which aligns its content to the right and
                        shrinks to fit its content.
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
            [:th {:class (when (= :right type) "right")}
             header])]]
        [:tbody
         (for [row rows]
           [:tr
            (for [{:keys [cell-fn type]} columns]
              [:td {:class (when (= :right type) "right")}
               (cell-fn row)])])]]))))
