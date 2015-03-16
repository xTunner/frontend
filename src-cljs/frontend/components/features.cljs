(ns frontend.components.features
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

(defn ui-example [name]
  [:img { :src (utils/cdn-path (str "/img/outer/features/ui-" name ".svg"))}])

(defn customer-image-src [shortname]
  (utils/cdn-path (str "/img/outer/customers/customer-" shortname ".svg")))

(defn testimonial
  [{:keys [company-name company-short customer-quote employee-name employee-title]}]
  [:div.testimonial
   [:img.customer-header {:src (customer-image-src company-short)
                          :alt company-name}]
   [:div.customer-quote {:class (str "quote-" company-short)}
    [:blockquote customer-quote]]
   [:div.customer-citation
    [:div.customer-employee-name employee-name]
    [:div
     employee-title
     " at "
     [:span.customer-company-name company-name]
     " – "
     [:a.customer-story {:href (str "/stories/" company-short)}
      "Read the story"]]]])

(defrender features [app owner]
  (html
    [:div#features
     [:div.jumbotron
      [:section.container
       [:div.row
        [:article.enterprise-hero-title.center-block
         [:div.text-center
          [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
         [:h1.text-center "Improve Productivity, Reduce Risk, and Scale with CircleCI"]
         [:h3.text-center "Let CircleCI help your team focus on making a great product. Speed up your testing and development cycle to improve productivity. CircleCI is flexible to run in your environment and scale with your growth. Have the peace of mind by reducing bugs and improving the quality of your application."]]]]

      [:div.row.text-center
       (common/sign-up-cta owner "features")]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-12
         [:h2.text-center
          "Speed and Productivity"
          [:br]
          [:small "Improve your team's productivity by shipping better code, faster."]]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "parallel")
         [:h3.text-center "Fast, automatic parallelism and intelligent test splitting"]
         [:p "CircleCI is the fastest way to run your test suite no matter how large. We divide up your tests intelligently based on average execution time and run an even workload on multiple containers in parallel, all on the fastest hardware available."]]
        [:div.feature
         (common/feature-icon "setup")
         [:h3.text-center "Quick & Easy Setup"]
         [:p "Set up CircleCI in minutes instead of days. Just sign up, add a project on GitHub, and start building and testing!"]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "github")
         [:h3.text-center "Seamless GitHub Integration"]
         [:p "View build status from GitHub or see PRs and commit messages from CircleCI."]]
        [:div.feature
         (common/feature-icon "ui")
         [:h3.text-center "Beautiful User Experience"]
         [:p "Users love our UI."]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "fail")
         [:h3.text-center "Real-time Build Output and Detailed Test Failure Data"]
         [:p "Thorough diagnostic information makes it easier to discover the root causes of failures quickly and iterate faster."]]
        [:div.feature
         (common/feature-icon "notification")
         [:h3.text-center "Smart Notifications"]
         [:p "In addition to standard email notifications, our chat integrations support a modern ChatOps-style workflow. Notifications let you know right away exactly which test failed so that you can fix it fast."]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "packages")
         [:h3.text-center "Inferred Test Commands and Pre-installed Packages and Services"]
         [:p "We speak your language. We know to run `npm test` if you have a test section in your `package.json`, and if you use Postgres in your Rails app, we'll make sure that a Postgres instance is spun up in your test container. Fully customizable and ready to test quickly."]]
        [:div.feature
         (common/feature-icon "key")
         [:h3.text-center "SSH Access"]
         [:p "Don't worry about tracking down a sysadmin or a build engineer to beg them for temporary access to your CI machine. CircleCI lets any developer ssh into CI with ease to troubleshoot any issues."]]]]]

     [:div.outer-section.section-dark.section-img
      [:section.container
       [:div.row
        (ui-example "dash")]]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-8.col-xs-offset-2
         (testimonial
           {:company-name "Shopify"
            :company-short "shopify"
            :logo-src nil
            :customer-quote "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customers, not maintaining CI Infrastructure."
            :employee-name "John Duff"
            :employee-title "Director of Engineering"})]]]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-12
         [:h2.text-center
          "Scalability and Flexibility"
          [:br]
          [:small "As your team grows, CircleCI is there to scale and grow with you."]]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "scale")
         [:h3.text-center "Start free and scale without limit"]
         [:p "Start with one build container for free, and dial up the capacity as you gfeature-row."]]
        [:div.feature
         (common/feature-icon "free")
         [:h3.text-center "Free for OSS"]
         [:p "CircleCI supports open source projects with 3 free containers for high build throughput."]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "yaml")
         [:h3.text-center "YAML-based Config"]
         [:p "Customize your build as much as you like with a simple YAML-based config file that you check into version control."]]
        [:div.feature
         (common/feature-icon "api")
         [:h3.text-center "RESTful API and Webhooks"]
         [:p "Use our extensive API to retrieve artifacts, check build outcomes, or trigger builds with dynamic environment variables."]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "artifacts")
         [:h3.text-center "Build Artifacts"]
         [:p "Store built binaries, code coverage reports, test output, or anything else you want as build artifacts on CircleCI. They will be easily accessible forever in the UI or through the REST API."]]
        [:div.feature
         (common/feature-icon "key-hole")
         [:h3.text-center "Sudo Support"]
         [:p "There's a good chance you have root access on the machines you deploy to, so why should CI be any different? CircleCI provides sudo support for all commands run on builds."]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "mobile")
         [:h3.text-center "iOS and Android"]
         [:p "Test both iOS and Android versions of your mobile app and any backend services, all on CircleCI."]]
        [:div.feature
         (common/feature-icon "docker")
         [:h3.text-center "Docker"]
         [:p "Push or pull Docker images from your build environment, or build and run containers right on CircleCI."]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "security")
         [:h3.text-center "Deployment Keys and Secrets"]
         [:p "Any sensitive information needed for tests and deployment can be securely stored and encrypted by CircleCI."]]]]]
     [:div.outer-section.section-dark.section-img
      [:section.container
       (ui-example "build-1")]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-8.col-xs-offset-2
         (testimonial
           {:company-name "Sincerely"
            :company-short "sincerely"
            :customer-quote "We never merge until we get that green checkmark. A pull request without CircleCi is like skydiving without a parachute."
            :employee-name "Justin Watt"
            :employee-title "Director of Engineering"})
         ]]
       ]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-12
         [:h2.text-center
          "Quality and Peace of Mind"
          [:br]
          [:small "Merge with confidence and reduce risk with continuous integration."]]]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "environment")
         [:h3.text-center "Clean environment for every build"]
         [:p "The CircleCI cache offers a balance of building in a \"clean room\" environment while caching dependencies for speed. The cache can be cleared on demand with much less hassle than \"resetting\" a conventional CI server."]]
        [:div.feature
         (common/feature-icon "support")
         [:h3.text-center "Support from engineers"]
         [:p "You get support from the engineers that build the product. We've seen pretty much everything at this point, and we're happy to help you with any issues running your tests on CircleCI."]
         ]]
       [:div.feature-row
        [:div.feature.feature-offset
         (common/feature-icon "deploy-1")
         [:h3.text-center "Continuous Deployment"]
         [:p "Stop losing sleep over \"big bang\" deployments. Reduce risk by deploying a single atomic, understandable change at a time."]]
        [:div.feature
         (common/feature-icon "badge")
         [:h3.text-center "Status badges for Open Source"]
         [:p "In the world of open source, status badges indicating the use of CI have become a standard of excellence for open source projects. They also serve as one more place to easily view current build status."]
         ]]]]
     [:div.outer-section.section-dark.section-img
      [:section.container
       (ui-example "build-2")]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-8.col-xs-offset-2
         (testimonial
           {:company-name "Kickstarter"
            :company-short "kickstarter"
            :customer-quote "CircleCI was super simple to set up and we started reaping the benefits immediately. It lets us ship code quickly and confidently."
            :employee-name "Aaron Suggs"
            :employee-title "Operations Engineer"})
         ]]]]
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.col-xs-12
         [:h2.text-center
          "Devs rely on us to just work. CircleCI supports your technology stack."
          [:br]
          [:small "Languages, Databases, Queus, Browsers, Deployment; we support all of your tools. The best teams in the world trust us as their continuous integration and delivery partner because of our unmatched support and flexibility."]]]]
       [:div.row]]]
     [:div.outer-section
      [:section.container
       [:div.col-xs-12
        [:h2.text-center "Build better code. Start shipping faster."]
        [:div.text-center
         (common/sign-up-cta owner "features")]]]]]))
