(ns frontend.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.test :refer-macros [use-fixtures]]
            [clojure.string]
            [frontend.components.project.test-common]
            [frontend.components.test-build-head]
            [frontend.components.test-org-settings]
            [frontend.components.test-statuspage]
            [frontend.components.test-insights]
            [frontend.utils-test]
            [frontend.controllers.controls-test]
            [frontend.controllers.ws-test]
            [frontend.models.test-plan]
            [frontend.models.test-feature]
            [frontend.controllers.api-test]
            [frontend.test-datetime]
            [frontend.test-routes]
            [frontend.test-pusher]
            [frontend.analytics.test-track]
            [frontend.analytics.test-segment]
            [frontend.analytics.test-core]))

(aset js/window "renderContext" "{}")
(aset js/window "SVGInjector" (fn [node] node))

(doo-tests 'frontend.test-pusher
           'frontend.components.project.test-common
           'frontend.components.test-build-head
           'frontend.components.test-org-settings
           'frontend.components.test-statuspage
           'frontend.components.test-insights
           'frontend.utils-test
           'frontend.controllers.controls-test
           'frontend.controllers.ws-test
           'frontend.models.test-plan
           'frontend.models.test-feature
           'frontend.controllers.api-test
           'frontend.test-datetime
           'frontend.test-routes
           'frontend.analytics.test-track
           'frontend.analytics.test-segment
           'frontend.analytics.test-core)
