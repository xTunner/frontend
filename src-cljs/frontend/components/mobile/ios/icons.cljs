(ns frontend.components.mobile.ios.icons
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

(def apple
  (html [:img.apple {:src (utils/cdn-path "/img/outer/mobile/ios/apple.svg")
                     :alt "Build iOS Apps"}]))

(def xcode
  (html [:img.xcode {:src (utils/cdn-path "/img/outer/mobile/ios/xcode.svg")
                     :alt "Automate Testing"}]))

(def deploy
  (html [:img.deploy {:src (utils/cdn-path "/img/outer/mobile/ios/deploy.svg")
                      :alt "Test on our cloud"}]))

(def iphone
  (html [:img.iphone {:src (utils/cdn-path "/img/outer/mobile/ios/iphone.svg")
                      :alt "iPhone"}]))
