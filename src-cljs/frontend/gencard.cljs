(ns frontend.gencard
  (:require [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [goog.object :as gobject]))

(def ignored-props #{"title" "href" "onClick"})
(defn signature [element]
  (let [props (gobject/get element "props")
        children (->> (gobject/get props "children")
                      js/React.Children.toArray
                      (filter js/React.isValidElement)
                      (filter #(string? (gobject/get % "type"))))]
    (into {:type (gobject/get element "type")
           :children (map signature children)}
          (map (juxt identity (partial gobject/get props))
               (remove (conj ignored-props "children")
                       (gobject/getKeys props))))))

(defn morph-data
  ([component-factory-var] (morph-data component-factory-var {}))
  ([component-factory-var overrides]
   (let [sample-size 100

         spec (:args (s/get-spec component-factory-var))

         ;; Wrap the factory. If it's a simple function returning a React DOM
         ;; element (an element with a string type), keep it as is. Otherwise
         ;; we've got a composite element (one whose type is a class), and we
         ;; need to (shallow) render it to get DOM elements to examine.
         component-factory
         (fn [props & children]
           (let [elt (apply (deref component-factory-var) props children)]
             (if (string? (gobject/get elt "type"))
               elt
               (let [renderer (js/React.addons.TestUtils.createRenderer)]
                 (.render renderer elt)
                 (.getRenderOutput renderer)))))

         groups (->> (gen/sample-seq (s/gen spec overrides))
                     (take sample-size)
                     (group-by (comp signature (partial apply component-factory))))]
     (when (> sample-size (count groups))
       (->> groups
            (sort-by (comp hash key))
            vals
            (map first))))))
