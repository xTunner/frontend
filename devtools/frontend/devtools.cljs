(ns frontend.devtools
  (:require devtools.core
            dirac.runtime))

; this enables additional features, :custom-formatters is enabled by default
(defn setup! []
  (devtools.core/install! [:custom-formatters :sanity-hints])
  (dirac.runtime/install!))

(setup!)
