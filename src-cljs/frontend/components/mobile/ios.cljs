(ns frontend.components.mobile.ios
  (:require [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.mobile :as mobile]
            [frontend.components.mobile.icons :as mobile-icons]
            [frontend.components.mobile.ios.icons :as icons]
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
       [:div.mobile.ios.page
        (common/nav owner)
        (mobile/nav :ios)
        [:section.intro.odd-section {}
         [:article {}
          [:h1.mobile-tagline "iOS App Testing, Done Better"]
          icons/apple
          [:p "Shipping your app on iOS is hard. The App Store review process is
long and painful. It’s important to get your app built right the first time to avoid
bugs and those nasty 1-star reviews. Let CircleCI help your iOS app development
cycle with our expertise in Continuous Integration and Continuous Delivery."
[:br]
           "*In beta - " [:a {:href (auth-url) :on-click (track-auth-fn owner)} "sign up"] " for the pilot program by marking your project
iOS and you'll automatically be added to the beta."]
          [:a.home-action.signup
           {:href (auth-url) :role "button" :on-click (track-auth-fn owner)}
           "Sign up for free"]]]
        [:section.pitch {}
         [:article {}
          [:h2 "More testing, fewer bugs, better iOS apps."]
          [:p.explain "Each time you push new code to your repo on Github for your
iOS app, CircleCI will automatically build and test your changes."]
          [:p.explain "More testing leads to fewer bugs. Ship your app with more confidence by continuously testing to ease the pain of the App Store review process."]
          [:a.home-action.documentation
           {:href "/docs/ios"
            :role "button"}
           "iOS Documentation"]]
         [:section.features
          [:article.feature-list
           [:h2 "Everything you've come to expect from CircleCI, just on OS X."]
           [:div.feature
            icons/deploy
            [:h3 "Test your app on our dedicated iOS cloud"]
            [:p "Running a dedicated build box is expensive and time consuming.
Let us manage the build environment. You build great iOS apps."]]
           [:div.feature
            icons/xcode
            [:h3 "Automate Testing"]
            [:p "CircleCI supports any environment that you work in and the most
recent version(s) of the iOS toolchain (including XCode 6.x). You can build with
xcodebuild, xctool, CocoaPods, or git submodules."]]]]]

        [:section.conclusion.odd-section {}
         [:a.signup.home-action {:href (auth-url) :role "button" :on-click (track-auth-fn owner)}
          "Sign up for free"]
         [:h3 "Start shipping faster, build for free using CircleCI today."]
         icons/iphone]]))))
