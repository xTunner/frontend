(ns frontend.analytics)

(defmacro deftrack [name args & body]
  `(defn ~name ~args
     (when (frontend.config/analytics-enabled?)
       ~@body)))
