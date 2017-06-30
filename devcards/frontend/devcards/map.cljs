(ns frontend.devcards.map
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
            [loom.alg :as alg]
            [loom.graph :as g])
  (:require-macros
   [devcards.core :as dc :refer [defcard]]
   [sablono.core :refer [html]]))

;; First: Can we classify these into columns?

(defn roots [g]
  (filter (comp zero? (partial g/in-degree g)) (g/nodes g)))

(defn ranks [g]
  (->> {:remaining-graph g}
       (iterate (fn [{g :remaining-graph}]
                  {:rank (roots g)
                   :remaining-graph (g/remove-nodes* g (roots g))}))
       (drop 1)
       (map :rank)
       (take-while seq)))

(def json
  "[
  \"checkout_code\",
  {
    \"clojure_dependencies\": {
      \"requires\": [
        \"checkout_code\"
      ]
    }
  },
  {
    \"npm_bower_dependencies\": {
      \"requires\": [
        \"checkout_code\"
      ]
    }
  },
  {
    \"clojure_test\": {
      \"requires\": [
        \"clojure_dependencies\",
        \"checkout_code\"
      ]
    }
  },
  {
    \"cljs_test\": {
      \"requires\": [
        \"clojure_dependencies\",
        \"npm_bower_dependencies\",
        \"checkout_code\",
        \"cljsbuild_test\"
      ]
    }
  },
  {
    \"cljsbuild_test\": {
      \"requires\": [
        \"clojure_dependencies\",
        \"checkout_code\"
      ]
    }
  },
  {
    \"cljsbuild_whitespace\": {
      \"requires\": [
        \"clojure_dependencies\",
        \"checkout_code\"
      ]
    }
  },
  {
    \"cljsbuild_production\": {
      \"requires\": [
        \"clojure_dependencies\",
        \"checkout_code\"
      ]
    }
  },
  {
    \"precompile_assets\": {
      \"requires\": [
        \"clojure_dependencies\",
        \"npm_bower_dependencies\",
        \"cljsbuild_whitespace\",
        \"cljsbuild_production\",
        \"checkout_code\"
      ]
    }
  },
  {
    \"deploy\": {
      \"requires\": [
        \"precompile_assets\",
        \"cljs_test\",
        \"clojure_test\",
        \"checkout_code\"
      ]
    }
  }
]")

(defn transitive-reduction [g]
  (reduce
   (fn [g [start end :as edge]]
     (let [without-edge (g/remove-edges g edge)]
       (if (alg/shortest-path without-edge start end)
         without-edge
         g)))
   g (g/edges g)))

(defn graph->draw-io [g]
  (->> g
       g/edges
       (map (fn [[from to]]
              (str from "->" to)))
       (string/join "\n")))

(def frontend-graph
  (apply g/digraph
         (->> json
              js/JSON.parse
              js->clj
              (map #(if (string? %) {% {"requires" []}} %))
              (map first)
              (mapcat (fn [[to {froms "requires"}]]
                        (for [from froms]
                          [from to]))))))

(defcard full-data
  (str "```\n" (graph->draw-io frontend-graph) "\n```"))

(defcard reduced-data
  (str "```\n" (graph->draw-io (transitive-reduction frontend-graph)) "\n```"))


#_(defcard num-map
  (let [g (g/digraph [1 2] [2 3] [2 4] [3 5] [4 5] 6)]
    (ranks g)

    (pr-str (alg/shortest-path g 1 6))))

(defn show-columns [things-in-columns]
  (html
   [:div {:style {:display "flex"}}
    (for [things things-in-columns]
      [:div
       (for [thing things]
         (if thing
           [:div {:style {:margin "0.5em"
                          :padding "0.5em"
                          :border "1px solid black"}}
            thing]
           [:div {:style {:margin "0.5em"
                          :padding "0.5em"
                          :height "calc(1em + 23px)"}}]))])]))

(defn show-columns-svg [things-in-columns]
  (let [width 150
        height 40]
    (html
     [:svg {:width "100%"
            :height "500"}
      (for [[x things] (map-indexed vector things-in-columns)]
        (for [[y thing] (map-indexed vector things)]
          (if thing
            [:rect {:style {:stroke "black"
                            :fill "none"
                            :width width
                            :height height
                            :x (* x (+ width 10))
                            :y (* y (+ height 10))}}])))])))

(defn has-far-arrows? [graph columns node]
  (zero? (g/out-degree (g/subgraph graph (conj (apply concat (rest columns)) node))
                       node)))

(defn build-columns [{:keys [graph remaining-ranks columns]}]
  (if (empty? remaining-ranks)
    columns
    (let [{far-arrows false no-far-arrows true}
          (group-by #(has-far-arrows? graph columns %) (first remaining-ranks))
          next-column
          (if far-arrows
            (let [height-to-clear (->> (conj columns no-far-arrows) (map count) (apply max))
                  padding (- height-to-clear (count no-far-arrows))]
              (concat no-far-arrows (take padding (repeat nil)) far-arrows))
            no-far-arrows)]
      (recur
       {:graph graph
        :remaining-ranks (rest remaining-ranks)
        :columns (conj columns next-column)}))))

(defcard map
  (let [g (transitive-reduction frontend-graph)
        columns (build-columns {:graph g
                                :remaining-ranks (reverse (ranks g))
                                :columns ()})]
    (html
     [:div
      (show-columns columns)
      (show-columns-svg columns)])))
