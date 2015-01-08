(ns frontend.components.mobile.ios.icons
  (:require [frontend.utils :as utils])
  (:require-macros [frontend.utils :refer [html]]))

(def apple
  (html [:img.apple {:src (utils/cdn-path "/img/outer/mobile/ios/apple.svg")
                     :alt "Mobile iOS app testing"}]))

(def xcode
  (html [:img.xcode {:src (utils/cdn-path "/img/outer/mobile/ios/xcode.svg")
                     :alt "Build iOS apps how you want"}]))

(def deploy
  (html [:img.deploy {:src (utils/cdn-path "/img/outer/mobile/ios/deploy.svg")
                      :alt "Test your iOS app in the cloud"}]))

(def iphone
  (html [:img.iphone {:src (utils/cdn-path "/img/outer/mobile/ios/iphone.svg")
                      :alt "Free iOS Mobile Continuous Integration with CircleCI"}]))
