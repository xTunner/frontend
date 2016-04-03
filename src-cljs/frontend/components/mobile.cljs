(ns frontend.components.mobile
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn mobile-cta [owner source]
  [:div.outer-section.outer-section-condensed.wide-cta-banner
   [:section
    [:h3 "Start shipping faster, build for free using CircleCI today."]
    (om/build common/sign-up-cta {:source source})]])

(defrender mobile [app owner]
  (html
    [:div.product-page.mobile.overview
     [:div.jumbotron
      [:h1 "Ship Better Mobile Apps, Faster."]
      [:p "Mobile apps live and die by their app store ratings. "
       "Nothing guarantees an app’s failure like a shipped bug and "
       "1-star reviews. Let CircleCI bring our deep knowledge and "
       "experience of Continuous Integration and Continuous Delivery "
       "to your mobile application development by automating the "
       "build, test, and deploy pipeline for your "
       [:a {:href "/mobile/ios"} "iOS"]
       " and "
       [:a {:href "/mobile/android"} "Android"]
       " applications."]
      (om/build common/sign-up-cta {:source "mobile"})]

     [:div.outer-section
      [:section.container
       [:div.overview
        [:h2.text-center "More testing, faster feedback, better releases."]
        [:p "The mobile development workflow can be frustrating and slow. "
         "App review times add significant delays to shipping, and prevent "
         "pushing fixes quickly to address shipped bugs and issues. It’s "
         "important to get the app built correctly to ensure a great user "
         "experience and better app ratings."]
        [:ol.steps
         [:li "Automate Build Process"]
         [:li "Create a Consistent Build Environment"]
         [:li "One Click Deployment" [:br] "(coming soon)"]]]]]

     [:div.outer-section.outer-section-condensed
      [:section.container
       [:div.how-it-works
        [:div.explanation
         [:h2 "How it works"]
         [:p "Every time you push new code to your project repo on GitHub, "
          "we automatically build and test your changes to make sure you didn’t "
          "break anything. For every green build, you can one-click deploy that "
          "successful version via Hockey, Testflight, Crashlytics or other "
          "over-the-air (OTA) deployment service (coming soon) to start "
          "collecting feedback immediately with no support from engineering."]]
        [:div.diagram shared/stories-procedure]]]]

     [:div.outer-section
      [:section.container
       [:div.feature-row
        [:div.feature
         (common/feature-icon "app-store")
         [:h3 "Improve App Store Rating"]
         [:p "Use Continuous Integration to reduce bugs so you ship "
          "great apps that your customers love."]]
        [:div.feature
         (common/feature-icon "sudo")
         [:h3 "Automate Testing"]
         [:p "Continuous Integration and Deployment fully automates the mobile "
          "app delivery process and significantly simplifies and accelerates "
          "the process of getting 5-star apps into the hands of your users."]]
        [:div.feature
         (common/feature-icon "controls")
         [:h3 "Inferred Project Setup"]
         [:p "Easily set up projects. Just like CircleCI for web apps, we infer "
          "your project settings without the developer having to do the setup. "
          "You can still set up your environment using the yml."]]
        [:div.feature
         (common/feature-icon "setup")
         [:h3 "Full Control"]
         [:p "You have full control to customize exactly what you need, whether it's your build tool, package manager, or system dependencies. If you can do it on your server, you can do it on ours."]]
        [:div.feature
         (common/feature-icon "commit")
         [:h3 "Merge With Confidence"]
         [:p "Monitor test status with every commit, and only merge when all the tests pass."
          "Feel productive instead of anxious when you press \"merge\"."]]
        [:div.feature
         (common/feature-icon "circle-success")
         [:h3 "Automate Deployment"]
         [:p "Continuous Integration and Deployment fully automates the mobile "
          "app delivery process and significantly simplifies and accelerates the "
          "process of getting 5-star apps into the hands of your users."]]]]]
     (mobile-cta owner "mobile")]))

(defn platform [owner data]
  [:div.product-page.mobile.platform {:class (:name data)}
   [:div.jumbotron
    [:h1 (:headline data)]
    (:jumbotron-content data)
    (om/build common/sign-up-cta {:source (str "mobile/" (:name data))})]

   [:div.outer-section
    [:section.container
     [:div.overview
      [:h2 (:subheadline data)]
      [:p (:explanation data)]
      (let [doc-link (:documentation data)]
        [:a.home-action.documentation
         {:href (:href doc-link)}
         (:title doc-link)])]]

    [:section.container
     [:div.overview
      [:h2 (:features-heading data)]]]

    [:section.container
     [:div.feature-row
      (for [feature (:features data)]
        [:div.feature
         (common/feature-icon (:icon feature))
         [:h3 (:heading feature)]
         [:p (:description feature)]])]]]
   (mobile-cta owner (str "mobile/" (:name data)))])

(defrender ios [app owner]
  (html
    (platform owner {:name "ios"
                     :headline "iOS App Testing, Done Better."
                     :jumbotron-content [:p
                                         "Shipping your app on iOS is hard. The App Store review process is
                                         long and painful. It’s important to get your app built right the first time to avoid
                                         bugs and those nasty 1-star reviews. Let CircleCI help your iOS app development
                                         cycle with our expertise in Continuous Integration and Continuous Delivery."
                                         [:br]
                                         [:br]
                                         "Currently in beta. Interested in joining? Please "
                                         [:a {:href "mailto:sayhi@circleci.com?subject=ios"} "contact us."]]
                     :subheadline "More testing, fewer bugs, better iOS apps."
                     :explanation "Each time you push new code to your repo on Github for your
                                  iOS app, CircleCI will automatically build and test your
                                  changes. More testing leads to fewer bugs. Ship your app with
                                  more confidence by continuously testing to ease the pain of the
                                  App Store review process."
                     :documentation {:title "iOS Documentation"
                                     :href "/docs/ios"}
                     :features-heading "Everything you've come to expect from CircleCI, just on OS X."
                     :features [{:icon "circle"
                                 :heading "Test your app on our dedicated iOS cloud"
                                 :description "Running a dedicated build box is expensive and time consuming.
                                              Let us manage the build environment. You build great iOS apps."}
                                {:icon "xcode"
                                 :heading "Automate Testing"
                                 :description "CircleCI supports any environment that you work in and the most
                                              recent version(s) of the iOS toolchain (including XCode 6.x). You can build with
                                              xcodebuild, xctool, CocoaPods, or git submodules."}]})))

(defrender android [app owner]
  (html
    (platform owner {:name "android"
                     :headline "Android App Testing, Done Better."
                     :jumbotron-content [:p
                                         "Building and testing an Android app
                                         isn't easy. CircleCI preinstalls the
                                         Android SDK to help you build and test
                                         your apps quickly. Avoid 1-star
                                         reviews by ensuring that your apps are
                                         bug-free the first time."]
                     :subheadline "Faster testing, better code, better Android apps."
                     :explanation "CircleCI will automatically build and test
                                  your changes for your Android appeach time
                                  you push new code to your repo on Github.
                                  More testing leads equals less bugs. Ship
                                  your app with confidence by continually
                                  testing and shipping great code."
                     :documentation {:title "Android Documentation"
                                     :href "/docs/android"}
                     :features-heading "The same CircleCI you love, for Android."
                     :features [{:icon "circle"
                                 :heading "Easy Setup"
                                 :description "CircleCI preinstalls the Android
                                              SDK, a number of SDK packages,
                                              and the NDK, so you don't have to."}
                                {:icon "xcode"
                                 :heading "Testing Android Projects"
                                 :description "Quickly setup the Android Emulator."}]})))
