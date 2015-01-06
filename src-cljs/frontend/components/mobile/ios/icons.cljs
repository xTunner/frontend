(ns frontend.components.mobile.ios.icons
  (:require [frontend.utils :as utils])
  (:require-macros [frontend.utils :refer [html]]))

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
