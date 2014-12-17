(ns frontend.components.mobile.icons
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.common :as common]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(def app-store
  (html [:img.app-store {:src (utils/cdn-path "/img/outer/mobile/app-store.svg")
                         :alt "Improve App Store Rating"}]))

(def build-env
  (html [:img.build-env {:src (utils/cdn-path "/img/outer/mobile/build-envs.svg")
                         :alt "Control your mobile app testing environment"}]))

(def commit
  (html [:img.commit {:src (utils/cdn-path "/img/outer/mobile/commit.svg")
                      :alt "Merge App code with confidence"}]))

(def deploy
  (html [:img.deploy {:src (utils/cdn-path "/img/outer/mobile/deploy.svg")
                      :alt "Automate Mobile Continuous Deployment"}]))

(def htc
  (html [:img.htc {:src (utils/cdn-path "/img/outer/mobile/htc.svg")
                   :alt "Mobile Android app testing"}]))

(def iphone
  (html [:img.iphone {:src (utils/cdn-path "/img/outer/mobile/iphone.svg")
                      :alt "Mobile iPhone app testing"}]))

(def nexus
  (html [:img.nexus {:src (utils/cdn-path "/img/outer/mobile/nexus.svg")
                     :alt "Mobile Android app testing"}]))

(def setup
  (html [:img.setup {:src (utils/cdn-path "/img/outer/mobile/setup.svg")
                     :alt "Easy Continuous Integration setup"}]))

(def steps
  (html [:img.steps {:src (utils/cdn-path "/img/outer/mobile/steps.svg")
                     :alt "Mobile Continuous Integration and Deployment"}]))

(def testing
  (html [:img.testing {:src (utils/cdn-path "/img/outer/mobile/testing.svg")
                       :alt "Automate App Testing"}]))

(def workflow
  (html [:img.workflow {:src (utils/cdn-path "/img/outer/mobile/workflow.svg")
                        :alt "Mobile Continuous Integration Workflow"}]))
