(ns frontend.test-runner
  (:require [doo.runner :refer-macros [doo-all-tests]]))

(aset js/window "renderContext" "{}")
(aset js/window "SVGInjector" (fn [node] node))

(doo-all-tests)
