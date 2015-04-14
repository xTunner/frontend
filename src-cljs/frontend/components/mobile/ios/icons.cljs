(ns frontend.components.mobile.ios.icons
  (:require [frontend.utils :as utils])
  (:require-macros [frontend.utils :refer [html]]))

(def apple
  (utils/outer-svg "mobile/ios/apple"
                   "Mobile iOS app testing"
                   {:class "apple"}))

(def xcode
  (utils/outer-svg "mobile/ios/xcode"
                   "Build iOS apps how you want"
                   {:class "xcode"}))

(def deploy
  (utils/outer-svg "mobile/ios/deploy"
                   "Test your iOS app in the cloud"
                   {:class "deploy"}))

(def iphone
  (utils/outer-svg "mobile/ios/iphone"
                   "Free iOS Mobile Continuous Integration with CircleCI"
                   {:class "iphone"}))
