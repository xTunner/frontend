(ns frontend.gencard
  (:require [cljs.core.async :as async :refer [chan]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [goog.object :as gobject]
            [medley.core :refer [distinct-by]]))

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

(defn- shallow-render
  "Shallow-renders the component factory. If the factory actually returns a
  React DOM element rather than a custom component element, returns that."
  [factory props & children]
  (let [elt (apply factory props children)]
    (if (string? (gobject/get elt "type"))
      elt
      (let [renderer (js/React.addons.TestUtils.createRenderer)]
        (.render renderer elt)
        (.getRenderOutput renderer)))))

(defn morph-data
  ([ch component-factory-var] (morph-data ch component-factory-var {}))
  ([ch component-factory-var overrides]
   (let [sample-size 100
         spec (:args (s/get-spec component-factory-var))
         samples (take sample-size (gen/sample-seq (s/gen spec overrides)))
         signature-of-sample (comp signature (partial apply shallow-render (deref component-factory-var)))
         morphs-ch (chan 1 (comp
                            (map (juxt signature-of-sample identity))
                            (distinct-by first)))]
     (async/onto-chan morphs-ch samples)
     (async/pipe morphs-ch ch))))
