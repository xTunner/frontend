(ns frontend.core-dev
  (:require [frontend.core :as core]
            [figwheel-sidecar.repl-api :as fig.repl]))

(defn -main
  "An alternate entry into the application which specifically
  starts up common developer tooling etc."
  []
  (fig.repl/start-figwheel!)
  (core/-main))
