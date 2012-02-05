(ns circle.backend.build.test-notify
  (:use midje.sweet)
  (:require [circle.backend.build.test-utils :as test])
  (:use [circle.backend.build.test-utils :only (minimal-build test-ns-setup)])
  (:require [circle.backend.build.notify :as notify])
  (:use [circle.backend.action :only (defaction)])
  (:require [circle.model.build :as build])
  (:use [circle.backend.build.run :only (run-build)])
  (:use circle.backend.build.config))

(test-ns-setup)

(fact "notify email works"
  (let [build (circle.backend.build.config/build-from-url (-> test/test-project :vcs_url))]
    (build/successful? (run-build build)) => truthy
    (notify/send-email-build-notification build) => truthy))
