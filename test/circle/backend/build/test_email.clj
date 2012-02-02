(ns circle.backend.build.test-email
  (:use midje.sweet)
  (:require [circle.backend.build.test-utils :as test])
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:require [circle.backend.build.email :as email])
  (:use [circle.backend.action :only (defaction)])
  (:require [circle.model.build :as build])
  (:use [circle.backend.build.run :only (run-build)])
  (:use circle.backend.build.config))

(fact "notify email works"
  (let [build (circle.backend.build.config/build-from-url (-> test/test-project :vcs_url))]
    (build/successful? (run-build build)) => truthy
    (circle.backend.build.email/send-email-build-notification build) => truthy))