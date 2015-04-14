(ns frontend.components.mobile.icons
  (:require [frontend.utils :as utils])
  (:require-macros [frontend.utils :refer [html]]))

(def app-store
  (utils/outer-svg "mobile/app-store"
                   "Improve App Store Rating"
                   {:class "app-store"}))

(def build-env
  (utils/outer-svg "mobile/build-envs"
                   "Control your mobile app testing environment"
                   {:class "build-env"}))

(def commit
  (utils/outer-svg "mobile/commit"
                   "Merge App code with confidence"
                   {:class "commit"}))

(def deploy
  (utils/outer-svg "mobile/deploy"
                   "Automate Mobile Continuous Deployment"
                   {:class "deploy"}))

(def htc
  (utils/outer-svg "mobile/htc"
                   "Mobile Android app testing"
                   {:class "htc"}))

(def iphone
  (utils/outer-svg "mobile/iphone"
                   "Mobile iPhone app testing"
                   {:class "iphone"}))

(def nexus
  (utils/outer-svg "mobile/nexus"
                   "Mobile Android app testing"
                   {:class "nexus"}))

(def setup
  (utils/outer-svg "mobile/setup"
                   "Easy Continuous Integration setup"
                   {:class "setup"}))

(def steps
  (utils/outer-svg "mobile/steps"
                   "Mobile Continuous Integration and Deployment"
                   {:class "steps"}))

(def testing
  (utils/outer-svg "mobile/testing"
                   "Automate App Testing"
                   {:class "testing"}))

(def workflow
  (utils/outer-svg "mobile/workflow"
                   "Mobile Continuous Integration Workflow"
                   {:class "workflow"}))
