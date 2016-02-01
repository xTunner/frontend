(ns frontend.devtools
  (:require [devtools.core :as devtools]))

; this enables additional features, :custom-formatters is enabled by default
(defn setup! []
  (devtools/enable-feature! :sanity-hints :dirac)
  (devtools/install!))

(setup!)
