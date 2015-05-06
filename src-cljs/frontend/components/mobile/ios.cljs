(ns frontend.components.mobile.ios
  (:require [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.mobile :as mobile]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn track-auth-fn [owner]
  #(raise! owner [:track-external-link-clicked
                 {:event "Auth GitHub"
                  :path (auth-url)}]))

(defn ios [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.product-page.mobile.ios
         [:div.jumbotron
          [:h1 "iOS App Testing, Done Better"]
          [:p
           "Shipping your app on iOS is hard. The App Store review process is
           long and painful. It’s important to get your app built right the first time to avoid
           bugs and those nasty 1-star reviews. Let CircleCI help your iOS app development
           cycle with our expertise in Continuous Integration and Continuous Delivery."
           [:br]
           "*In beta - "
           [:a {:href (auth-url) :on-click (track-auth-fn owner)} "sign up"]
           " for the pilot program by marking your project iOS and you'll automatically be added to the beta."]
          (common/sign-up-cta owner "mobile")]

         [:div.outer-section
          [:section.container
           [:div.overview
            [:h2 "More testing, fewer bugs, better iOS apps."]
            [:p "Each time you push new code to your repo on Github for your
                iOS app, CircleCI will automatically build and test your
                changes. More testing leads to fewer bugs. Ship your app with
                more confidence by continuously testing to ease the pain of the
                App Store review process."]
            [:a.home-action.documentation
             {:href "/docs/ios"}
             "iOS Documentation"]]]

          [:section.container
           [:div.overview
            [:h2 "Everything you've come to expect from CircleCI, just on OS X."]]]

          [:section.container
           [:div.feature-row
            [:div.feature
             (common/feature-icon "circle")
             [:h3 "Test your app on our dedicated iOS cloud"]
             [:p "Running a dedicated build box is expensive and time consuming.
                 Let us manage the build environment. You build great iOS apps."]]
            [:div.feature
             (common/feature-icon "xcode")
             [:h3 "Automate Testing"]
             [:p "CircleCI supports any environment that you work in and the most
                 recent version(s) of the iOS toolchain (including XCode 6.x). You can build with
                 xcodebuild, xctool, CocoaPods, or git submodules."]]]]]
         (om/build mobile/mobile-cta "mobile")]))))
