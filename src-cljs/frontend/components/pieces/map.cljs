(ns frontend.components.pieces.map
  (:require [clojure.string :as string]
            [loom.alg :as alg]
            [loom.graph :as g])
  (:require-macros
   [devcards.core :as dc :refer [defcard]]
   [frontend.utils :refer [html]]))

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

(defn transitive-reduction [g]
  (reduce
   (fn [g [start end :as edge]]
     (let [without-edge (g/remove-edges g edge)]
       (if (alg/shortest-path without-edge start end)
         without-edge
         g)))
   g (g/edges g)))

(defn move-to [x y]
  (str "M" x "," y))

(defn line-to [x y]
  (str "L" x "," y))

(defn arc-to [rx ry x-axis-rotate large-arc-flag sweep-flag x y]
  (str "A" rx "," ry "," x-axis-rotate "," large-arc-flag "," sweep-flag "," x "," y))

(defn arrow [start end strut-pos radius]
  [:path.arrow
   {:fill "none"
    :d (cond
         (> (second start) (second end))
         (str
          (apply move-to start)
          (line-to (- strut-pos radius) (second start))
          (arc-to radius radius 0 0 0 strut-pos (- (second start) radius))
          (line-to strut-pos (+ (second end) radius))
          (arc-to radius radius 0 0 1 (+ strut-pos radius) (second end))
          (apply line-to end))

         (< (second start) (second end))
         (str
          (apply move-to start)
          (line-to (- strut-pos radius) (second start))
          (arc-to radius radius 0 0 1 strut-pos (+ (second start) radius))
          (line-to strut-pos (- (second end) radius))
          (arc-to radius radius 0 0 0 (+ strut-pos radius) (second end))
          (apply line-to end))

         :else
         (str
          (apply move-to start)
          (apply line-to end)))}])

(defn box-x-position [{:keys [box-width x-spacing]} x]
  (* x (+ box-width x-spacing)))

(defn box-y-position [{:keys [box-height y-spacing]} y]
  (* y (+ box-height y-spacing)))

(defn arrow-start [{:keys [box-width box-height] :as config} x y]
  [(+ box-width (box-x-position config x))
   (+ (/ box-height 2) (box-y-position config y))])

(defn arrow-end [{:keys [box-height] :as config} x y]
  [(box-x-position config x)
   (+ (/ box-height 2) (box-y-position config y))])

(defn strut-offset [{:keys [strut-spacing]} columns tx ty]
  (* strut-spacing (- (count (nth columns tx)) ty)))

(defn strut-position [config columns tx ty]
  (- (first (arrow-end config tx ty)) (strut-offset config columns tx ty)))

(defn map-layout [{:keys [box-width box-height arrow-radius] :as config} columns edges]
  (let [coords (into {}
                     (map-indexed
                      (fn [x column]
                        (map-indexed
                         (fn [y thing]
                           [thing [x y]])
                         column))
                      columns))]
    {:arrows
     (for [[from to] edges
           :let [[fx fy] (get coords from)
                 [tx ty] (get coords to)]]
       {:start (arrow-start config fx fy)
        :end (arrow-end config tx ty)
        :strut-position (strut-position config columns tx ty)
        :radius arrow-radius})

     :boxes
     (for [[x things] (map-indexed vector columns)
           [y thing] (map-indexed vector things)
           :when thing]
       {:x (box-x-position config x)
        :y (box-y-position config y)
        :width box-width
        :height box-height
        :content thing})}))

(defn map-svg [{:keys [box-width box-height arrow-radius] :as config} columns edges]
  (let [{:keys [arrows boxes]} (map-layout config columns edges)]
    (html
     [:div {:style {:background "white"}}
      [:svg {:width "2000"
             :height "300"
             :style {:background "white"}}
       [:g {:transform "translate(3,3)"}
        (for [{:keys [start end strut-position radius]} arrows]
          (arrow start end strut-position radius))
        (for [{:keys [x y width height content]} boxes]
          [:g {:transform (str "translate(" x "," y ")")}
           [:rect.job {:fill "none"
                       :width width
                       :height height}]
           [:text {:x 10
                   :y 25}
            content]])]]])))

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

(dc/do
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

  #_(defcard full-data
      (str "```\n" (graph->draw-io frontend-graph) "\n```"))

  #_(defcard reduced-data
      (str "```\n" (graph->draw-io (transitive-reduction frontend-graph)) "\n```"))

  (defcard map
    (let [g (transitive-reduction frontend-graph)
          columns (build-columns {:graph g
                                  :remaining-ranks (reverse (ranks g))
                                  :columns ()})
          edges (conj (g/edges g)
                      ["cljsbuild_test" "precompile_assets"]
                      ["npm_bower_dependencies" "cljsbuild_whitespace"])
          config {:box-width 150
                  :box-height 40
                  :x-spacing 100
                  :y-spacing 10
                  :strut-spacing 20
                  :arrow-radius 10}]
      (html
       [:div
        #_(show-columns columns)
        (map-svg config columns (g/edges g))
        (map-svg config (-> columns
                            vec
                            (assoc 2 [nil "cljsbuild_test" "cljsbuild_whitespace" "cljsbuild_production" "clojure_test"]))
                 edges)
        (map-svg config (-> columns
                            vec
                            (update 1 conj nil)
                            (assoc 2 ["cljsbuild_whitespace" "cljsbuild_production" "cljsbuild_test" "clojure_test"])
                            (assoc 3 ["precompile_assets" "cljs_test"]))
                 edges)])))

  (defcard arrow
    (html
     [:div
      [:style
       "path.arrow {
        stroke-width: 2px;
        stroke: #CCCCCC;
      }

      rect.job {
        stroke-width: 1px;
        stroke: #333333;
        fill: #FFFFFF;
      }"]
      [:svg {:width "100%"
             :height "200"}
       (arrow [0 100] [200 10] 130 10)]
      [:svg {:width "100%"
             :height "200"}
       (arrow [0 10] [200 100] 130 10)]
      [:svg {:width "100%"
             :height "200"}
       (arrow [0 50] [200 50] 130 10)]])))
