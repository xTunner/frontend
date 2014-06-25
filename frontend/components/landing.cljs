(ns frontend.components.landing
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.env :as env]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :refer [auth-url]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(def hero-graphic
  [:div.row-fluid.how
   [:div.span4
    [:img {:height 150
           :width 150
           :src XXXX}]
    [:h4 "Push your new code to GitHub"]]
   [:div.span4
    [:img {:height 150
           :width 150
           :src XXXX}]
    [:h4 "Your tests run on the CircleCI platform"]]
   [:div.span4
    [:img {:height 150
           :width 150
           :src XXXX}]
    [:h4 "Deploy green builds to your servers"]]])

(defn home-button
  [{:keys [source]}]
  [:a.btn.btn-primary.bold-btn {:href XXXXX
                                :title "Sign up with GitHub"
                                ;; XXXXX on click track auth event
                                }
   [:i.fa.fa-github-alt]
   "Sign up with "
   [:strong.white "GitHub"]])

(def home-cta
  [:div.ctabox {:class (if first "line")}
   [:div
    [:p "Plans start at $19 per month. All plans include a free 14 day trial."]]
   (home-button {:source "hero"})
   [:div
    [:p
     [:i "CircleCI keeps your code safe."
      [:a {:href "/security" :title "Security"} "Learn how."]]]]])

(defn stories-procedure
  []
  (let [width 960
        height 480
        psize 200
        px (- (* (/ 1 3) width) (/ psize 2))
        pxr (- (* (/ 2 3) width) (/ psize 2))
        py (/ (- height psize) 2)]
    [:svg.stories-procedure {:xmlns "http://www.w3.org/2000/svg"
                             :viewBox (str "0 0 " width " " height)
                             :width width
                             :height height}
     [:path.slot.github {:d "M240,69.5c-18.2,0-33,14.8-33,33 c0,14.6,9.5,26.9,22.6,31.3c1.6,0.3,2.3-0.7,2.3-1.6c0-0.8,0-3.4,0-6.1c-9.2,2-11.1-3.9-11.1-3.9c-1.5-3.8-3.7-4.8-3.7-4.8 c-3-2,0.2-2,0.2-2c3.3,0.2,5.1,3.4,5.1,3.4c2.9,5,7.7,3.6,9.6,2.7c0.3-2.1,1.2-3.6,2.1-4.4c-7.3-0.8-15-3.7-15-16.3 c0-3.6,1.3-6.5,3.4-8.9c-0.3-0.8-1.5-4.2,0.3-8.7c0,0,2.8-0.9,9.1,3.4c2.6-0.7,5.5-1.1,8.3-1.1c2.8,0,5.6,0.4,8.3,1.1 c6.3-4.3,9.1-3.4,9.1-3.4c1.8,4.5,0.7,7.9,0.3,8.7c2.1,2.3,3.4,5.3,3.4,8.9c0,12.7-7.7,15.5-15.1,16.3c1.2,1,2.2,3,2.2,6.1 c0,4.4,0,8,0,9.1c0,0.9,0.6,1.9,2.3,1.6c13.1-4.4,22.5-16.7,22.5-31.3C273,84.2,258.2,69.5,240,69.5z"}]
     [:path.slot.developer {:d "M267.7,395.8c0,0-2.8-9.1-9.9-16.3c-2-2.1-7.3-4.2-10.9-1.1c-6.3,5.3-6.3,17.4-6.3,17.4 H267.7z M237,395.8c0-3.3-2.7-6-6-6c-2.7,0-4.9,1.8-5.7,4.2l-10.6,0c-0.1,0-0.6,0-1-0.4c-0.4-0.3-0.3-0.8-0.3-1.1 c0-0.1,0-0.1,0-0.1l0,0c1.7-6,5.7-15.3,6.2-16.1c0.1-0.2,0.5-0.5,0.8-0.6l0.3-0.1l0,0.2c1.9,8.9,3.8,12,3.8,12.2l0.2,0.3l2.5-0.5 l-5.9-28.3l-2.6,0.5l0,0.4c0,0.2-0.3,4.1,1.4,12.7c0,0,0,0,0,0l0.1,0.5l-0.4,0.2c-0.6,0.3-1.2,0.7-1.7,1.2c-0.5,0.6-4.4,9.6-6.6,17 c0,0.1,0,0.1,0,0.2c-0.2,1,0.1,1.9,0.6,2.6c0.9,1.1,2.3,1.1,2.5,1.1l13,0v0L237,395.8L237,395.8z M245.1,359.1l0.9,4.4 c1.6,0.4,2.8,1.8,2.8,3.5c0,2-1.6,3.6-3.6,3.6c-2,0-3.6-1.6-3.6-3.6c0-1.2,0.6-2.3,1.5-2.9l-0.9-4.8c-3.8,0.9-6.6,4.3-6.6,8.3 c0,4.7,3.8,8.6,8.6,8.6c4.7,0,8.6-3.8,8.6-8.6C252.7,363.3,249.4,359.6,245.1,359.1z M244.1,359.1c0.3,0,0.7,0,1,0.1l-0.6-3.3 l-2.9,0.6l0.6,2.9C242.8,359.2,243.4,359.1,244.1,359.1z"}]
     [:path.slot.users {:d "M726.9,374.7c2.8-2,4.6-5.4,4.6-9.1c0-6.2-5-11.2-11.2-11.2s-11.2,5-11.2,11.1 c0,3.7,1.8,7.1,4.6,9.2c-6.3,2.6-10.8,9-10.8,16.3c0,9.6,34.8,9.6,34.8,0C737.7,383.7,733.2,377.2,726.9,374.7z M739.7,374.9 c2-1.5,3.3-3.9,3.3-6.6c0-4.5-3.7-8.2-8.2-8.2c-1,0-2,0.2-2.9,0.5c0.6,1.5,1,3.2,1,4.9c0,3.2-1.2,6.3-3.4,8.7 c5.9,3.3,9.6,9.6,9.6,16.6c0,0.3,0,0.6-0.1,0.9c4.7-0.6,8.5-2.2,8.5-4.8C747.6,381.2,744.4,376.8,739.7,374.9z M701.4,390.8 c0-6.9,3.6-13,9.3-16.4c0.1-0.1,0.2-0.2,0.3-0.2c-2.1-2.3-3.3-5.4-3.3-8.6c0-1.7,0.3-3.3,0.9-4.7c-1-0.5-2.2-0.7-3.4-0.7 c-4.5,0-8.2,3.7-8.2,8.2c0,2.7,1.3,5.2,3.3,6.7c-4.6,1.9-7.9,6.3-7.9,11.9c0,2.7,4.2,4.4,9.1,4.9 C701.4,391.4,701.4,391.1,701.4,390.8z"}]
     [:path.slot.servers {:d "M697.5,96.2c0,0,0,7.4,0,9.2c0,3.6,10,6.6,22.4,6.6c12.4,0,22.4-3,22.4-6.6c0-1.8,0-9.2,0-9.2 s-5.9,5.6-22.4,5.6C701.2,101.7,697.5,96.2,697.5,96.2z M720,114.8c-18.7,0-22.4-5.6-22.4-5.6s0,10.7,0,12.5c0,3.6,10,6.6,22.4,6.6 c12.4,0,22.4-3,22.4-6.6c0-1.8,0-12.5,0-12.5S736.5,114.8,720,114.8z M719.7,123.7c-1.3,0-2.4-1.1-2.4-2.4c0-1.3,1.1-2.4,2.4-2.4 c1.3,0,2.4,1.1,2.4,2.4C722.1,122.7,721.1,123.7,719.7,123.7z M719.9,76.4c-12.4,0-22.4,3-22.4,6.6c0,0.4,7.6,7.9,32.4,4.8 c-5.6,2.1-24.7,3.1-32.4-3.1c0,2.4,0,6.1,0,7.5c0,3.6,10,6.6,22.4,6.6c12.4,0,22.4-3,22.4-6.6c0-1.8,0-7.4,0-9.2 C742.4,79.4,732.3,76.4,719.9,76.4z"}]
     [:path.slot.circleci {:d "M480,229.1c6.6,0,11.9,5.3,11.9,11.9c0,6.6-5.3,11.9-11.9,11.9c-6.6,0-11.9-5.3-11.9-11.9 C468.1,234.4,473.4,229.1,480,229.1z M480,191c-23.3,0-42.9,15.9-48.4,37.5c0,0.2-0.1,0.4-0.1,0.6c0,1.3,1.1,2.4,2.4,2.4H454 c1,0,1.8-0.6,2.2-1.4c0,0,0-0.1,0-0.1c4.2-9,13.2-15.2,23.8-15.2c14.5,0,26.2,11.7,26.2,26.2c0,14.5-11.7,26.2-26.2,26.2 c-10.5,0-19.6-6.2-23.8-15.2c0,0,0-0.1,0-0.1c-0.4-0.8-1.2-1.4-2.2-1.4h-20.2c-1.3,0-2.4,1.1-2.4,2.4c0,0.2,0,0.4,0.1,0.6 c5.6,21.6,25.1,37.5,48.4,37.5c27.6,0,50-22.4,50-50C530,213.4,507.6,191,480,191z"}]
     [:g.step.dev-to-github
      [:path.track {:d "M194.4,340.4 c-39.5-49.9-46.9-120.8-13.1-179.2c3.3-5.8,7-11.3,10.9-16.5"}]
      [:polygon.direction {:points "196.4,148.7 195.4,141.5 188.2,142.5"}]
      [:path.touch {:d "M194.5,365.4c-7.4,0-14.7-3.2-19.6-9.5c-22.7-28.7-36.4-63.2-39.4-99.9 c-3.1-37.5,5.3-74.7,24.2-107.4c3.8-6.6,8-13,12.6-19.1c8.3-11,24-13.2,35-4.9c11,8.3,13.2,24,4.9,35c-3.4,4.4-6.5,9.1-9.2,13.9 c-13.8,23.9-19.9,50.9-17.7,78.3c2.2,26.8,12.1,52,28.7,72.9c8.6,10.8,6.8,26.5-4.1,35.1C205.4,363.6,199.9,365.4,194.5,365.4z"}]
      #_[:foreignObject {:x (str px)
                       :y (str py)
                       :width (str psize)
                       :height (str psize)}
       [:p {:style (str psize "px")}
        "Developers commit new features to GitHub."]]]
     [:g.step.github-to-circle
      [:path.track {:d "M468.1,180.6 c-2.5-6-5.3-12-8.6-17.8c-32.9-58.9-97.6-88.9-160.6-80.5"}]
      [:polygon.direction {:points "462.4,180.6 469.2,183.4 472,176.6"}]
      [:path.touch {:d "M468.2,205.6c-9.9,0-19.2-5.9-23.2-15.6c-2.1-5.2-4.5-10.2-7.2-15.1 c-13.4-24.1-33.6-43.2-58.2-55.2c-24.1-11.8-50.8-16.2-77.3-12.7c-13.7,1.8-26.3-7.8-28.1-21.5s7.8-26.3,21.5-28.1 c36.3-4.8,72.9,1.2,105.9,17.3c33.8,16.6,61.4,42.8,79.8,75.8c3.7,6.6,7,13.6,9.9,20.6c5.2,12.8-0.9,27.4-13.7,32.6 C474.5,205,471.3,205.6,468.2,205.6z"}]
      #_[:foreignObject {:x px
                       :y py
                       :width psize
                       :height psize}
       [:p {:style (str "height: " psize "px")}
        "CircleCI creates a new build to test each commit."]]]
     [:g.step.failing
      [:path.track {:d "M300.5,399.8 c6.5,0.8,13.1,1.2,19.8,1.2c67.5,0,125.2-41.8,148.6-101"}]
      [:polygon.direction {:points "301.1,394.6 295.3,399.1 299.8,404.9"}]
      [:g.status
       [:circle.pin {:cx "400.7"
                     :cy "379"
                     :r "10"}]
       [:path.mask {:d "M405.4,381c0.4,0.4,0.4,1,0,1.3l-1.3,1.3c-0.4,0.4-1,0.4-1.3,0l-2-2l-2,2 c-0.4,0.4-1,0.4-1.3,0l-1.3-1.3c-0.4-0.4-0.4-1,0-1.3l2-2l-2-2c-0.4-0.4-0.4-1,0-1.3l1.3-1.3c0.4-0.4,1-0.4,1.3,0l2,2l2-2 c0.4-0.4,1-0.4,1.3,0l1.3,1.3c0.4,0.4,0.4,1,0,1.3l-2,2L405.4,381z"}]]
      [:path.touch {:d "M320.2,426c-7.6,0-15.3-0.5-22.8-1.4c-13.7-1.7-23.4-14.2-21.8-27.9 c1.7-13.7,14.2-23.4,27.9-21.8c5.5,0.7,11.2,1,16.7,1c27.6,0,54-8.3,76.6-23.9c22.1-15.3,38.9-36.5,48.8-61.3 c5.1-12.8,19.6-19.1,32.4-14c12.8,5.1,19.1,19.6,14,32.4c-13.5,34.1-36.6,63.1-66.8,84C394.4,414.7,358,426,320.2,426z"}]
      #_[:foreignObject {:x pxr
                       :y py
                       :width psize
                       :height psize}
       [:p {:style (str "height: " psize "px")}
        "Failed builds provide feedback to developers"]]]
     [:g.step.passing
      [:path.track {:d "M661.8,82.9 c-6.5-0.9-13.1-1.4-19.7-1.4c-67.5-0.8-125.7,40.4-149.7,99.3"}]
      [:polygon.direction {:points "659.1,87.4 664.9,82.9 660.4,77.1"}]
      [:g.status
       [:circle.pin {:cx "560.9"
                     :cy "101.9"
                     :r "10"}]
       [:path.mask {:d "M567,99.9l-5.4,5.4l-1.3,1.3c-0.4,0.4-1,0.4-1.3,0l-1.3-1.3l-2.7-2.7c-0.4-0.4-0.4-1,0-1.3 l1.3-1.3c0.4-0.4,1-0.4,1.3,0l2,2l4.7-4.7c0.4-0.4,1-0.4,1.3,0l1.3,1.3C567.4,98.9,567.4,99.5,567,99.9z"}]]
      [:path.touch {:d "M492.3,205.8c-3.2,0-6.4-0.6-9.5-1.9c-12.8-5.2-18.9-19.8-13.7-32.6 c13.9-33.9,37.3-62.7,67.7-83.3c31.2-21.1,67.7-32,105.4-31.6c7.6,0.1,15.3,0.6,22.8,1.7c13.7,1.8,23.3,14.4,21.4,28.1 c-1.8,13.7-14.4,23.3-28.1,21.4c-5.5-0.7-11.1-1.2-16.7-1.2c-27.6-0.3-54.1,7.6-76.9,23c-22.2,15-39.3,36-49.5,60.8 C511.5,199.9,502.2,205.8,492.3,205.8z"}]
      #_[:foreignObject {:x pxr
                       :y py
                       :width psize
                       :height psize}
       [:p.right {:style (str "height: " psize "px")}
        "New features are deployed once all tests pass."]]]
     [:g.step.servers-to-users
      [:path.track {:d "M767.8,337.5 c3.9-5.2,7.6-10.7,10.9-16.5c33.7-58.5,26.2-129.4-13.4-179.2"}]
      [:polygon.direction {:points "772.5,339.7 765.2,340.7 764.2,333.5"}]
      [:path.touch {:d "M767.8,362.5c-5.2,0-10.5-1.6-15-5c-11-8.3-13.2-24-4.9-35c3.3-4.4,6.5-9.1,9.2-14 c13.7-23.9,19.8-51,17.5-78.3c-2.2-26.7-12.2-51.9-28.8-72.9c-8.6-10.8-6.8-26.5,4-35.1c10.8-8.6,26.5-6.8,35.1,4 c22.8,28.7,36.5,63.2,39.5,99.8c3.1,37.5-5.2,74.7-24,107.4c-3.8,6.6-8,13-12.6,19.1C782.8,359,775.4,362.5,767.8,362.5z"}]
      #_[:foreignObject {:x pxr
                       :y py
                       :width psize
                       :height psize}
       [:p.right {:style (str "height: " psize "px")}
        "Users constantly have access to latest features."]]]]))

(defn home-hero-unit
  [ab-tests]
  [:div#hero
   [:div.container
    [:h1 "Ship better code, faster"]
    (if (:top-copy ab-tests)
      [:h3 "Continuous Integration and Deployment that just works. Signup and get running in under 60 seconds."]
      [:h3 "CircleCI gives web developers powerful Continuous Integration and Deployment with easy setup and maintenance."])
    (if (:hero-graphic ab-tests)
      hero-graphic
      (stories-procedure))
    [:div.row-fluid
     [:div.hero-unit-cta
      home-cta]]]])

(defn customer-image [customer-name image]
  [:div.big-company
   [:img {:title customer-name
          :src image}]])

(def home-customers
 [:section.customers
  [:div.container
   [:div.row.span12]
   [:div.center
    [:div.were-number-one
     [:h2
      "CircleCI is the #1 hosted continuous deployment provider.But don't take our word for it, read what our awesome customers have to say."]]]
   [:div.customers-trust.row
    [:h4 [:span "Trusted By"]]
    (customer-image "Salesforce" "img/logos/salesforce.png")
    (customer-image "Samsung" "img/logos/samsung.png")
    (customer-image "Kickstarter" "img/logos/kickstarter.png")
    (customer-image "Cisco" "img/logos/cisco.png")
    (customer-image "Shopify" "img/logos/shopify.png")
    [:span.stretch]]
  [:div.container
   [:div.row
    [:div.customer.span4.well
     [:p
      "CircleCI has significantly improved our testing infrastructure. Tests finish faster. We add new projects rapidly and continuous integration happens from the get-go. "
      [:strong "I'm a huge fan."]]
     " + $c(HAML.john_collison_image())"
     [:h4 "John Collison"]
     [:p
      "Founder at "
      [:a
       {:title "Stripe",
        :href "https://stripe.com/",
        :alt-there "Stripe"}
       "Stripe"]]]
    [:div.customer.span4.well
     [:p
      "It's super fun and helpful to see test results hit our Hipchat room a few minutes after a push. "
      [:strong "The first time it happened, my team cheered."]
      "The fact that we don't have to admin the server ourselves is a big timesaver."]
     " + $c(HAML.jon_crawford_image())"
     [:h4 "Jon Crawford"]
     [:p
      "CEO of "
      [:a
       {:title "Storenvy",
        :href "http://www.storenvy.com/",
        :alt "Storenvy"}
       "Storenvy"]]]
    [:div.customer.span4.well
     [:p
      "CircleCI was super simple to set up and we started reaping the benefits immediately. It lets us ship code quickly and confidently. "
      [:strong "CircleCI's customer support is outstanding."]
      "We get quick, thorough answers to all our questions."]
     " + $c(HAML.aaron_suggs_image())"
     [:h4 "Aaron Suggs"]
     [:p
      "Operations Engineer at "
      [:a
       {:title "Kickstarter",
        :href "http://www.kickstarter.com/",
        :alt "Kickstarter"}
       "Kickstarter"]]]]]]
  [:a.customer-story-link.center-text {:href "stories/shopify"}
   "Read how Shopify grew with Circle"]])

(def home-features
  [:div.benefits
   [:div.container
    [:div.row-fluid
     [:div.span12
      [:h2 "Features & Benefits of CircleCI"]
      [:h3
       "A professional continuous integration setup for your team today, tomorrow and beyond."]]]
    [:div.row-fluid
     [:div.clearfix.quick-setup.section.span4
      [:div.section-graphic [:i.fa.fa-magic]]
      [:div.section-content
       [:h3 "Quick Setup"]
       [:p
        [:strong "Set up your continuous integration in 20 seconds"]
        ", not two days. With one click CircleCI detects test settings for a wide range of web apps, and sets them up automatically on our servers."]]]
     [:div.clearfix.fast-tests.section.span4
      [:div.section-graphic [:i.fa.fa-bolt]]
      [:div.section-content
       [:h3 "Fast Tests"]
       [:p
        "Your productivity relies on fast test results. CircleCI runs your tests"
        [:strong "faster than your Macbook Pro"]
        ", EC2, your local server, or any other service."]]]
     [:div.clearfix.deep-customization.section.span4
      [:div.section-graphic [:i.fa.fa-flask]]
      [:div.section-content
       [:h3 "Deep Customization"]
       [:p
        "Real applications often deviate slightly from standard configurations, so CircleCI does too. Our configuration is so flexible that it's easy to "
        [:strong "tweak almost anything"]
        " you need."]]]]
    [:div.row-fluid
     [:div.clearfix.debug-with-ease.section.span4
      [:div.section-graphic [:i.fa.fa-cog]]
      [:div.section-content
       [:h3 "Debug with Ease"]
       [:p
        "When your tests are broken, we help you get them fixed. We auto-detect errors, have great support, and even allow you to "
        [:strong "SSH into our machines"]
        " to test manually."]]]
     [:div.clearfix.section.smart-notifications.span4
      [:div.section-graphic [:i.fa.fa-bullhorn]]
      [:div.section-content
       [:h3 "Smart Notifications"]
       [:p
        "CircleCI intelligently notifies you via email, Hipchat, Campfire and more. You won't be flooded with useless notifications about other people's builds and passing tests, "
        [:strong "we only notify you when it matters."]]]]
     [:div.clearfix.incredible-support.section.span4
      [:div.section-graphic [:i.fa.fa-heart]]
      [:div.section-content
       [:h3 "Loving support"]
       [:p
        "We respond to support requests as soon as possible, every day. Most requests get a response responded to "
        [:strong "within an hour."]
        " No-one ever waits more than 12 hours for a response."]]]]
    [:div.row-fluid
     [:div.automatic-parallelization.clearfix.section.span4
      [:div.section-graphic [:i.fa.fa-fullscreen]]
      [:div.section-content
       [:h3 "Automatic Parallelization"]
       [:p
        "We can automatically parallelize your tests across multiple machines. "
        [:strong "With up to 16-way parallelization"]
        ", your test turn-around time can be massively reduced."]]]
     [:div.clearfix.continuous-deployment.section.span4
      [:div.section-graphic [:i.fa.fa-refresh]]
      [:div.section-content
       [:h3 "Continuous Deployment"]
       [:p
        [:strong "Get code to your customers faster"]
        ", as soon as the tests pass. CircleCI supports branch-specific deployments, SSH key management and supports any hosting environment using custom commands, auto-merging, and uploading packages."]]]
     [:div.clearfix.more-to-come.section.span4
      [:div.section-graphic [:i.fa.fa-lightbulb-o]]
      [:div.section-content
       [:h3 "More to come."]
       [:p
        "At CircleCI we are always listening to our customers for ideas and feedback. If there is a specific feature or configuration ability you need, we want to know."]]]]]])

(def home-technology
  [:section.technology
   [:div.container
    [:h2 "We support your stack"]
    [:div.tabbable
     [:div.row-fluid
      [:div.nav-tabs-container.span12
       [:ul#tech.nav.nav-tabs
        [:li.active
         [:a
          {:language "language",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#languages"}
          "Languages"]]
        [:li
         [:a
          {:databases "databases",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#databases"}
          "Databases"]]
        [:li
         [:a
          {:queues "queues",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#queues"}
          "Queues"]]
        [:li
         [:a
          {:browsers "browsers",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#browsers"}
          "Browsers"]]
        [:li
         [:a
          {:libraries "libraries",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#libraries"}
          "Libraries"]]
        [:li
         [:a
          {:deployment "deployment",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#deployment"}
          "Deployment"]]
        [:li
         [:a
          {:custom "custom",
           :tab:_ "tab:_",
           :properties:_ "properties:_",
           :stack "stack",
           :test "test",
           :event:_ "event:_",
           :data-bind "\\track:",
           :data-toggle "tab",
           :href "#custom"}
          "Custom"]]]]]
     [:div.tab-content
      [:div#languages.active.tab-pane
       [:div.row-fluid
        [:div.span6 " + $c(HAML.languages_image())"]
        [:div.span6
         "CircleCI automatically infers how to run your Ruby, Node, Python and Java tests.You can customize any step, or set up your test steps manually for PHP or other languages."]]]
      [:div#databases.tab-pane
       [:div.row-fluid
        [:div.span6 " + $c(HAML.dbs_image())"]
        [:div.span6
         "If you use any of the dozen most common databases, we have them pre-installed for you, set up to be trivial to use.Postgres and MySQL have their permissions set and are running, Redis, Mongo and Riak are running for you, and the other DBs are just a configuration switch away."]]]
      [:div#queues.tab-pane
       [:div.fluid.row
        [:div.span6 " + $c(HAML.queues_image())"]
        [:div.span6
         "If you need to test against queues, we have them installed on our boxes.We support RabbitMQ and Beanstalk, have Redis installed so you can use Resque, and can install anything else you need."]]]
      [:div#browsers.tab-pane
       [:div.row-fluid
        [:div.span6 " + $c(HAML.browsers_image())"]
        [:div.span6
         "We support continuous integration testing in your apps against a wide range of browsers.We have latest Chrome, Firefox and webkit installed using xvfb, as well as PhantomJS and CasperJS.Headless browser testing is completely supported, so you can test using Selenium, directly against PhantomJS, or using abstraction layers such as Capybara and Cucumber."]]]
      [:div#libraries.tab-pane
       [:div.row-fluid
        [:div.offset2.span8
         [:p
          "We run a recent version Ubuntu and have installed all of the libraries you need for development.We have common libs like libxml2, uncommon ones like libblas, and downright rare libraries like libavahi-compat-libdnssd-dev.As new important libraries come out it's trivial for us to add them for you."]]]]
      [:div#deployment.tab-pane
       [:div.row-fluid
        [:div.span6 " + $c(HAML.deployment_image())"]
        [:div.span6
         "Continuous Deployment means that you can deploy your fresh code to production fast and with no fear.Many of our customers deploy directly after a green push to master or another branch.We manage SSH keys and allow you to deploy any way you wish, whether directly to a PaaS, using Capistrano, Fabric, or arbitrary bash commands, or – for you autoscalers – by auto-merging to another branch, or packaging code up to S3."]]]
      [:div#custom.tab-pane
       [:div.row-fluid
        [:div.offset2.span8
         [:p
          "Although we do our best to set up your tests in one click, occasionally developers have custom setups.Need to use npm in your PHP project?Using Haskell?Use a specific Ruby patchset?Do you depend on private repos?We have support for dozens of different ways to customize, and we make it trivial to customize basically anything.Custom language versions, environment variables, timeouts, packages, databases, commands, etc, are all trivial to set up."]]]]]]]])  

(def home-get-started
  [:div.get-started
   [:div.container
    [:div.row
     [:div.offset3.span6
      [:div.box
       [:h2 "Get Started"]
       [:hr]
       [:p.center
        [:strong "Set up your continuous integration in 20 seconds."]]
       [:ol
        [:li "Choose a GitHub repository."]
        [:li "Watch your tests run faster than ever."]
        [:li "Get your team into the flow."]]
       [:div.center
        [:div.main-cta
         [:div.ctabox
          [:a.btn.btn-action-orange.btn-jumbo
           {:event:_ "event:_",
            :source:_ "source:_",
            :auth "auth",
            :ci.github.authurl "ci.github.authurl",
            :github "github",
            :get_started_section "get_started_section",
            :properties:_ "properties:_",
            :data-bind "\\attr:",
            :href:_ "href:_",
            :track_link:_ "track_link:_"}
           "RUN YOUR TESTS"]]]]
       [:p.center
        [:i
         "CircleCI keeps your code safe. "
         [:a
          {:title "Privacy and Security", :href "/privacy"}
          "Learn how."]]]
       [:p.center
        "Plans start at "
        [:i "$19 per month"]
        [:br]
        "All plans include a "
        [:strong [:i "Free 14 Day Trial."]]]]]]]])

(utils/defrender home
  []
  (let [ab-tests nil]
    (html [:div.landing.page
           [:div.banner]
           [:div
            (home-hero-unit ab-tests)
            home-customers
            home-features
            home-technology
            home-get-started]])))


