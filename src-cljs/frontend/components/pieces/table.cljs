(ns frontend.components.pieces.table
  (:require [devcards.core :as dc :refer-macros [defcard-om]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

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

  :columns  - A sequence of column descriptions. Each is a map with the following keys:
              :header  - The content which should appear in the header cell of the column.
              :cell-fn - A function which, given a row object, returns the content for that
                         row's cell in this column.
              :type    - A column type, or a collection of types. Available types:
                         :right  - Column aligns its content to the right. Without this
                                   type, the column will align left.
                         :shrink - Column width shrinks to fit its content. Columns without
                                   :shrink will share any leftover space.
  :rows     - A sequence of objects which will each generate a row. These will be passed to
              the columns' :cell-fns to generate each cell.
  :key-fn   - A function of a row object which returns a value to use as the React
              key for the row.
  :striped? - (optional) Renders the table with alternating white/gray row stripes."
  [{:keys [columns rows key-fn striped?]} owner]
  {:pre (fn? key-fn)}
  (reify
    om/IRender
    (render [_]
      (component
        (html
         [:table {:class (when striped? "striped")}
          [:thead
           [:tr
            (for [[idx {:keys [header type]}] (map-indexed vector columns)]
              ;; We never reorder columns in a table, so the index works as a
              ;; React key. If we ever do reorder columns, we'll need to come up
              ;; with a key to identify them.
              [:th {:key idx
                    :class (cell-classes type)}
               header])]]
          [:tbody
           (for [row rows]
             [:tr (when key-fn {:key (key-fn row)})
              (for [[idx {:keys [cell-fn type]}] (map-indexed vector columns)]
                [:td {:key idx
                      :class (cell-classes type)}
                 (cell-fn row)])])]])))))

(defn action-button
  "A button suitable for the action button cell of a table row.

  label    - The textual label. Not visible; used as an aria-label.
  icon     - The icon rendered visually as the button.
  on-click - Handler called when the button is clicked."
  [label icon on-click]
  (html
   [:button {:data-component `action-button
             :aria-label label
             :on-click on-click}
    icon]))

(dc/do
  (defn format-date [date]
    (.toDateString date))

  (defn table-parent [data owner]
    (om/component
      (om/build table {:key-fn :name
                       :rows [{:name "John"
                               :birthday (js/Date. "1940-10-09")}
                              {:name "Paul"
                               :birthday (js/Date. "1942-06-18")}
                              {:name "George"
                               :birthday (js/Date. "1943-02-25")}
                              {:name "Ringo"
                               :birthday (js/Date. "1940-07-07")}]
                       :columns [{:header "Name"
                                  :cell-fn :name}
                                 {:header "Birthday"
                                  :cell-fn (comp format-date :birthday)}
                                 {:type :shrink
                                  :cell-fn (fn [beatle]
                                             (action-button
                                               "Remove"
                                               "X"
                                               #(js/alert (str "You may not remove " (:name beatle) " from the band."))))}]})))

  (defcard-om table
    table-parent))
