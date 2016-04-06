(ns frontend.components.press
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(def news-items
  [{:date "2/15/2015"
    :publisher "Venture Beat"
    :title "Codeship launches ParallelCI, a tool that lets developers test code way, way faster"
    :link "http://venturebeat.com/2015/02/12/codeship-parallelci/"}

   {:date "12/17/2014"
    :publisher "The New Stack"
    :title "CircleCI buys Distiller to Support Mobile App Development"
    :link "http://thenewstack.io/circleci-buys-distiller-to-support-mobile-app-development/"}

   {:date "11/12/2014"
    :publisher "Venture Beat"
    :title "Amazon teases a Github-like code-repository service in its big public cloud"
    :link "http://venturebeat.com/2014/11/12/amazon-continuous-integration/"}

   {:date "8/25/2014"
    :publisher "VentureBeat"
    :title "Here’s the latest on VMware’s public cloud: databases, storage, & more"
    :link "http://venturebeat.com/2014/08/25/heres-the-latest-on-vmwares-public-cloud-databases-storage-more/"}

   {:date "8/20/2014"
    :publisher "Dr. Dobb's Journal"
    :title "CircleCI Adds Docker For Continuous Delivery"
    :link "http://www.drdobbs.com/tools/circleci-adds-docker-for-continuous-deli/240168896"}

   {:date "8/14/2014"
    :publisher "CIO"
    :title "How to Expedite Continuous Testing"
    :link "http://www.cio.com/article/2464032/development-tools/how-to-expedite-continuous-testing.html"}

   {:date "4/10/2014"
    :publisher "InfoWorld"
    :title "Programmer picks: 7 great GitHub integrations"
    :link "http://www.infoworld.com/slideshow/147333/programmer-picks-7-great-github-integrations-239832"}

   {:date "3/17/2014"
    :publisher "TechRepublic"
    :title "Startup hiring: How to build your 'A-Team'"
    :link "http://www.techrepublic.com/article/startup-hiring-how-to-build-your-a-team/"}

   {:date "3/6/2014"
    :publisher "The First Word on Tech"
    :title "GitHub Developer Program emphasizes integrations"
    :link "http://www.infoworld.com/t/development-tools/github-developer-program-emphasizes-integrations-237816"}

   {:date "2/7/2014"
    :publisher "Fortune Term Sheet"
    :title "Deals of the Day: Carlyle buys big Illinois package"
    :link "http://finance.fortune.cnn.com/2014/02/07/deals-of-the-day-carlyle-buys-big-illinois-package/"}

   {:date "2/7/2014"
    :publisher "Pulse 2.0"
    :title "CircleCI raises $6 million in funding"
    :link "http://pulse2.com/2014/02/07/circleci-103046/"}

   {:date "2/7/2014"
    :publisher "VentureBeat"
    :title "Drone.io’s shift hints at the future of sending software to clouds"
    :link "http://venturebeat.com/2014/02/07/droneio/"}

   {:date "2/7/2014"
    :publisher "Vator News"
    :title "Funding roundup - week ending 2/7/14"
    :link "http://vator.tv/news/2014-02-07-funding-roundup-week-ending-2-7-14"}

   {:date "2/6/2014"
    :publisher "VentureBeat"
    :title "CircleCI raises $6M to ship out your company’s code faster"
    :link "http://venturebeat.com/2014/02/06/circleci-raises-6m-to-ship-out-your-companys-code-faster/"}

   {:date "2/6/2014"
    :publisher "GigaOM"
    :title "CircleCI nets $6M from DJF to take its continuous integration global"
    :link "http://gigaom.com/2014/02/06/circleci-nets-6m-from-djf-to-take-its-continuous-integration-global/"}

   {:date "2/6/2014"
    :publisher "VentureBeat"
    :title "Funding daily: It’s bloomin’ startups"
    :link "http://venturebeat.com/2014/02/06/funding-daily-its-bloomin-startups/"}

   {:date "11/9/2013"
    :publisher "GigaOM"
    :title "A quick reminder of why cloud computing really matters — the applications"
    :link "http://gigaom.com/2013/11/09/a-quick-reminder-of-why-cloud-computing-really-matters-the-applications/"}

   {:date "10/16/2013"
    :publisher "Xaprb"
    :title "Continuous integration and deployment"
    :link "http://www.xaprb.com/blog/2013/10/16/continuous-integration-and-deployment/"}

   {:date "7/25/2013"
    :publisher "CSS Tricks"
    :title "Website Deployment: Let Us Count The Ways!"
    :link "http://css-tricks.com/deployment/"}

   {:date "6/18/2013"
    :publisher "GigaOM"
    :title "Meet the heavyweight team behind Heavybit, a community for developer-focused startups"
    :link "http://gigaom.com/2013/06/18/meet-the-heavyweight-team-behind-heavybit-a-community-for-developer-focused-startups/"}

   {:date "6/18/2013"
    :publisher "Wired"
    :title "‘Heavybit’ Teaches Hackers How to Be Business Savvy"
    :link "http://www.wired.com/wiredenterprise/2013/06/heavybit-industries/"}

{:date "4/20/2013"
 :publisher "Fast Company"
 :title "Why 2013 Is An Awesome Year To Start Up"
 :link "http://www.fastcolabs.com/3008619/open-company/why-2013-awesome-year-start"}

{:date "4/9/2013"
 :publisher "TechCrunch"
 :title "Cisimple Exits Beta, Makes Mobile App Building, Testing & Deployment…Well, Simple"
 :link "http://techcrunch.com/2013/04/09/cisimple-exits-beta-makes-mobile-app-building-testing-deployment-well-simple/"}

{:date "3/19/2013"
 :publisher "Cloud Times"
 :title "StartUp in Focus: CircleCI"
 :link "http://cloudtimes.org/2013/03/19/startup-in-focus-circleci/"}

{:date "3/10/2013"
 :publisher "Programmable Web"
 :title "30 New APIs: Intercom, EasyPost, and Jorum"
 :link "http://blog.programmableweb.com/2013/03/10/30-new-apis-intercom-easypost-and-jorum/"}

{:date "3/1/2013"
 :publisher "Vator News"
 :title "Funding roundup - week ending 03/1/13"
 :link "http://vator.tv/news/2013-03-01-funding-roundup-week-ending-03-1-13"}

{:date "2/27/2013"
 :publisher "PandoDaily"
 :title "Amazon’s Kindle glitch and the always-be-shipping-code mentality"
 :link "http://pandodaily.com/2013/02/27/amazons-kindle-glitch-and-the-always-be-shipping-code-mentality/"}

{:date "2/26/2013"
 :publisher "peHUB"
 :title "CircleCI Secures Seed Funds"
 :link "http://www.pehub.com/188231/circleci-secures-seed-funds/"}

{:date "2/25/2013"
 :publisher "TechCrunch"
 :title "CircleCI Raises $1.5M From Eric Ries And Heroku Founders For Platform To Test Web Apps"
 :link "http://techcrunch.com/2013/02/25/circleci-raises-1-5m-from-eric-ries-and-heroku-founders-for-platform-to-test-web-apps/"}

{:date "2/25/2013"
 :publisher "VentureBeat"
 :title "Funding Daily: Red carpet reflections"
 :link "http://venturebeat.com/2013/02/25/funding-daily-red-carpet-reflections/"}

{:date "2/25/2013"
 :publisher "GigaOM"
 :title "CircleCI gets $1.5M to build out continuous integration service"
 :link "http://gigaom.com/2013/02/25/circleci-gets-1-5m-to-build-out-continuous-integration-service/"}

{:date "2/25/2013"
 :publisher "AllThingsD"
 :title "CircleCI Raises $1.5M to Help Developers Push Code"
 :link "http://allthingsd.com/20130225/circleci-raises-1-5m-to-help-developers-push-code/"}

{:date "2/25/2013"
 :publisher "PandoDaily"
 :title "CircleCI raises $1.5 million to let developers test code faster"
 :link "http://pandodaily.com/2013/02/25/circleci-raises-1-5-million-to-let-developers-test-code-faster/"}

{:date "2/25/2013"
 :publisher "The Next Web"
 :title "‘Heroku for testing’ CircleCI nabs $1.5M round from founders and early investors of Heroku and others"
 :link "http://thenextweb.com/insider/2013/02/25/heroku-for-testing-circleci-nabs-1-5m-round-from-founders-and-early-investors-of-heroku-and-others/"}

{:date "9/24/2012"
 :publisher "Daily Tekk"
 :title "100 Terrific Tools for Coders & Developers"
 :link "http://dailytekk.com/2012/09/24/100-terrific-tools-for-coders-developers/"}])

(defn render-articles [items]
  (map
    (fn [item]
      (html
        [:div.news-item
         (:title item)
         [:a.news-link {:href (:link item)} (:date item)]]))
    items))

(defn press [app owner]
  (om/component
    (html
      [:div#press
       [:div.jumbotron
        common/language-background-jumbotron
        [:section.container
         [:div.row
          [:article.hero-title.center-block
           [:div.text-center
            [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]
            [:h1 "Newsroom"]
            [:h3
             "For media and market research analysts, please contact Laura Franzese at "
             [:a {:href "mailto:laura@circleci.com"} "laura@circleci.com"]
             " or "
             [:a {:href "tel:+18005857075"} "800.585.7075"]
             [:br]
             "Fast Facts - Founded in 2011 - "
             [:a {:href "/contact"} "HQ"]
             " in San Francisco CA - $7.5M raised"]]]]]]
       [:div.outer-section
        [:section.container
         [:h2.text-center "Latest press releases"]
         (render-articles
           [{:title "CircleCI Launches iOS and Android Support; Enables App Developers to Meet Rising Demand"
             :date "December 17, 2014"
             :link "http://www.marketwired.com/press-release/circleci-launches-ios-android-support-enables-app-developers-meet-rising-demand-1977825.htm"
             :publisher "CircleCI"}
            {:title "CircleCI Secures $6 Million Series A Funding to Accelerate Global Expansion"
             :date "Februrary 6, 2014"
             :link "http://www.businesswire.com/news/home/20140206006575/en/CircleCI-Secures-6-Million-Series-Funding-Accelerate#.VPkGgUJeSfQ"
             :publisher "CircleCI"}
            {:title "Cloud-Based Testing Startup CircleCI Announces $1.5M Seed Funding"
             :date "Februrary 25, 2013"
             :link "http://www.marketwired.com/press-release/cloud-based-testing-startup-circleci-announces-15m-seed-funding-1761028.htm"
             :publisher "CircleCI"}])]]
       [:div.outer-section.outer-section-condensed
        common/language-background
        [:section.container
         [:div.row
          [:div.col-xs-12.text-center
           [:h2 "Logos & resources"]
[:p "Cheers for not distorting or reconfiguring our logos, badges and headshots. We appreciate it."]]]
         [:div.row
          [:div.col-xs-4
           [:div.well.text-center
            [:h3 "CircleCI logos"]
            [:a {:href "https://github.com/circleci/media/tree/master/presskit/logos"}
             [:img {:src (utils/cdn-path "/img/outer/press/badge-logo.svg")
                    :height 75}]]
            [:h3 [:small "SVG, EPS"]]]]
          [:div.col-xs-4
           [:div.well.text-center
            [:h3 "CircleCI badges"]
            [:a {:href "https://github.com/circleci/media/tree/master/presskit/badges"}
             [:img {:src (utils/cdn-path "/img/outer/press/badge-fail.svg")
                    :height 75}]]
            [:h3 [:small "SVG, EPS"]]]]
          [:div.col-xs-4
           [:div.well.text-center
            [:h3 "CircleCI headshots"]
            [:a {:href "https://github.com/circleci/media/tree/master/presskit/headshots"}
             [:img.img-circle {:src (stefon/asset-path "/img/outer/about/paul.png")
                               :height 75
                               :width 75}]]
            [:h3 [:small "PNG"]]]]]]]
       [:div.outer-section
        [:h2.text-center "CircleCI in the news"]
        (render-articles news-items)]])))

