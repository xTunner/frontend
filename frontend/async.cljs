(ns frontend.async
  (:require [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def ^:dynamic *uuid* nil)

(defn put! [port val & args]
  (if (and (satisfies? IMeta val) *uuid*)
    (apply async/put! port (vary-meta val assoc :uuid *uuid*) args)
    (apply async/put! port val args)))

(defn filter-ch
  "Because apparently filter< is deprecated, and to be replaced by
   'transformers', which the docs don't mention. Maybe an earlier
   name for transducers?"
  [f in]
  (let [out (async/chan)]
    (go-loop []
      (let [val (<! in)]
        (when (f val)
          (>! out val)))
      (recur))
    out))
