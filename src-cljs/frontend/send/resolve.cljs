(ns frontend.send.resolve
  (:require [cljs.core.async :as async :refer [chan close! put!]]
            [cljs.core.async.impl.protocols :as async-impl]
            [goog.log :as glog]
            [om.next :as om]
            [promesa.core :as p :include-macros true])
  (:import goog.debug.Console))

(defonce *logger*
  (when ^boolean goog.DEBUG
    (.setCapturing (Console.) true)
    (glog/getLogger "frontend.send.resolve")))

(defn- read-port? [x]
  (implements? async-impl/ReadPort x))

(defn- pipe-values
  "Pipes value(s) from `from` to `to`, returning `to`. If `from` is a channel or
  a promise of a channel, pipes to that channel as with core.async/pipe. If
  `from` is a value or a promise of a value, puts that value on `to` and closes
  `to`."
  [from to]
  (p/then
   from
   (fn [v]
     (if (read-port? v)
       (async/pipe v to)
       (doto to (put! v) (close!)))))
  to)

(defn resolve [env query-or-ast channel]
  (let [ast (if (vector? query-or-ast)
              (om/query->ast query-or-ast)
              query-or-ast)
        resolvers (:resolvers env)
        children (:children ast)]
    (async/pipe
     (async/merge
      (for [ast children
            :let [read-from-key (get-in ast [:params :<] (:key ast))
                  ast (update ast :params dissoc :<)]]
        (if (contains? resolvers read-from-key)
          (let [resolver (get resolvers read-from-key)]
            (pipe-values (resolver env ast)
                         (chan 1 (map #(hash-map (:key ast) %)))))
          (if-let [[keys resolver]
                   (first (filter #(contains? (key %) read-from-key) resolvers))]
            (pipe-values (resolver env ast)
                         (chan 1 (comp
                                  (map #(get % read-from-key))
                                  (map #(hash-map (:key ast) %)))))
            (do
              (glog/error *logger* (str "No resolver found for key " read-from-key))
              (doto (chan) (close!)))))))
     channel)))
