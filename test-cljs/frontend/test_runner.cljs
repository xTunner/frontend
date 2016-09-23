(ns frontend.test-runner
  (:require [cljs.test :refer-macros [use-fixtures]]
            [clojure.string]
            [doo.runner :refer-macros [doo-tests]]
            [frontend.analytics.test-core]
            [frontend.analytics.test-segment]
            [frontend.analytics.test-track]
            [frontend.components.project.test-common]
            [frontend.components.test-build-head]
            [frontend.components.test-insights]
            [frontend.components.test-org-settings]
            [frontend.components.test-statuspage]
            [frontend.controllers.test-api]
            [frontend.controllers.test-controls]
            [frontend.controllers.test-ws]
            [frontend.models.test-action]
            [frontend.models.test-build]
            [frontend.models.test-feature]
            [frontend.models.test-plan]
            [frontend.models.test-project]
            [frontend.models.test-user]
            [frontend.test-datetime]
            [frontend.test-pusher]
            [frontend.test-routes]
            [frontend.utils-test]
            [frontend.utils.test-build]
            [frontend.utils.test-seq]))

(aset js/window "renderContext" "{}")
(aset js/window "SVGInjector" (fn [node] node))

(doo-tests 'frontend.analytics.test-core
           'frontend.analytics.test-segment
           'frontend.analytics.test-track
           'frontend.components.project.test-common
           'frontend.components.test-build-head
           'frontend.components.test-insights
           'frontend.components.test-org-settings
           'frontend.components.test-statuspage
           'frontend.controllers.test-api
           'frontend.controllers.test-controls
           'frontend.controllers.test-ws
           'frontend.models.test-action
           'frontend.models.test-build
           'frontend.models.test-feature
           'frontend.models.test-plan
           'frontend.models.test-project
           'frontend.models.test-user
           'frontend.test-datetime
           'frontend.test-pusher
           'frontend.test-routes
           'frontend.utils-test
           'frontend.utils.test-build
           'frontend.utils.test-seq)
