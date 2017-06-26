(ns frontend.devcards.morphs
  (:require [cljs.core.async :as async :refer [<! chan close!]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [goog.object :as gobject]
            [medley.core :refer [distinct-by]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

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

(defn friendly-pipe
  "Like core.async/pipe, but gives the browser an opportunity to run higher
  priority tasks between each element."
  ([from to] (friendly-pipe from to true))
  ([from to close?]
   (go-loop []
     (let [v (<! from)]
       (if (nil? v)
         (when close? (close! to))
         (when (>! to v)
           ;; This gives the browser a chance to break away from the pipe
           ;; process and return later.
           (<! (async/timeout 0))
           (recur)))))
   to))


(defn generate
  "Generates signature, morph pairs for component-factory-var (using optional
  generator overrides) and puts them onto ch."
  ([ch component-factory-var] (generate ch component-factory-var {}))
  ([ch component-factory-var overrides]
   (let [sample-size 100
         spec (:args (s/get-spec component-factory-var))
         samples (take sample-size (gen/sample-seq (s/gen spec overrides)))
         signature-of-sample (comp signature (partial apply shallow-render (deref component-factory-var)))
         samples-ch (chan)
         morphs-ch (chan 1 (comp
                            (map (juxt signature-of-sample identity))
                            (distinct-by first)))]
     (async/onto-chan samples-ch samples)
     (friendly-pipe samples-ch morphs-ch)
     (async/pipe morphs-ch ch))))


(defn- update-morphs [this props]
  (let [morph-ch (:morphs props)]
    (go-loop []
      (when-let [morph (<! morph-ch)]
        (om-next/update-state! this update :morphs conj morph)
        (recur)))))

(defui ^:once ^:private MorphDisplay
  Object
  (initLocalState [this]
    {:morphs {}})
  (componentDidMount [this]
    (update-morphs this (om-next/props this)))
  (componentWillReceiveProps [this next-props]
    (update-morphs this next-props))
  (render [this]
    (let [{:keys [render-morphs]} (om-next/props this)
          {:keys [morphs]} (om-next/get-state this)]
      (render-morphs (vals morphs)))))

(def ^:private morph-display (om-next/factory MorphDisplay))

(defn render
  "Renders morphs of component-factory-var (generated using optional generator
  overrides). render-fn will be called with a collection of morphs and should
  return a React element to render."
  ([component-factory-var render-fn]
   (render component-factory-var {} render-fn))
  ([component-factory-var overrides render-fn]
   (morph-display {:morphs (generate (chan) component-factory-var overrides)
                   :render-morphs render-fn})))
