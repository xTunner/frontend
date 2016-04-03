(ns frontend.components.integrations
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.docker :as docker]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.github :as gh-utils]
            ;; TODO: Remove; see :allowFullscreen TODO below.
            [goog.dom :as dom]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(def circle-logo
  [:svg#Layer_1 {:y "0px", :xml:space "preserve", :x "0px", :viewBox "0 0 100 100", :version "1.1", :enable-background "new 0 0 100 100"}
   [:path#mark_21_ {:fill "#054C64", :d "M49.5,39.2c5.8,0,10.5,4.7,10.5,10.5s-4.7,10.5-10.5,10.5c-5.8,0-10.5-4.7-10.5-10.5\n\tS43.7,39.2,49.5,39.2z M49.5,5.5c-20.6,0-38,14.1-42.9,33.2c0,0.2-0.1,0.3-0.1,0.5c0,1.2,0.9,2.1,2.1,2.1h17.8\n\tc0.9,0,1.6-0.5,1.9-1.2c0,0,0-0.1,0-0.1c3.7-7.9,11.7-13.4,21-13.4c12.8,0,23.2,10.4,23.2,23.2C72.7,62.6,62.3,73,49.5,73\n\tc-9.3,0-17.4-5.5-21-13.4c0,0,0-0.1,0-0.1c-0.3-0.7-1.1-1.2-1.9-1.2H8.7c-1.2,0-2.1,0.9-2.1,2.1c0,0.2,0,0.4,0.1,0.5\n\tC11.6,79.9,28.9,94,49.5,94C74,94,93.8,74.2,93.8,49.8S74,5.5,49.5,5.5z"}"/"]])

(def selenium-logo
  [:svg.selenium {:viewBox "0 0 100 100"}
   [:path {:d "M64.6,75.6c0,2.7-3.3,5.6-7.7,5.6 c-5.7,0-9.7-3-9.7-11.5c0-6.8,2.9-11.5,9.7-11.5c9.2,0,8,10.8,8,10.8H47.6 M15.9,73.9c0,4.1,5.2,6.8,10.9,6.8s9.6-3.1,9.6-8 s-3.7-6-9.6-7.8c-5.9-1.9-9.6-2.8-9.6-7.8s3.5-7.4,9.6-7.4c6.7,0,9.6,3.2,9.6,5.2 M44.3,33c0,0,12.7,12.7,12.7,12.7l38-38 M81.3,14.9c-2.3-2-5.4-3.3-8.7-3.3H18.9C11.5,11.7,5,17.9,5,25.2V79c0,7.4,6.5,13.3,13.9,13.3h53.8c7.4,0,13-5.9,13-13.3V25.2 c0-0.7,0.1-1.5,0-2.2"}]])

(def ie-logo
  [:svg.browser.ie {:viewBox "30 30 240 240"} [:path {:d "M256.25,151.953c0-16.968-4.387-32.909-12.08-46.761c32.791-74.213-35.136-63.343-38.918-62.603   c-14.391,2.816-27.705,7.337-39.986,13.068c-1.811-0.102-3.633-0.158-5.469-0.158c-45.833,0-84.198,31.968-94.017,74.823   c24.157-27.101,41.063-38.036,51.187-42.412c-1.616,1.444-3.198,2.904-4.754,4.375c-0.518,0.489-1.017,0.985-1.528,1.477   c-1.026,0.987-2.05,1.975-3.05,2.972c-0.595,0.593-1.174,1.191-1.76,1.788c-0.887,0.903-1.772,1.805-2.638,2.713   c-0.615,0.645-1.215,1.292-1.819,1.938c-0.809,0.866-1.613,1.733-2.402,2.603c-0.613,0.676-1.216,1.352-1.818,2.03   c-0.748,0.842-1.489,1.684-2.22,2.528c-0.606,0.7-1.207,1.4-1.801,2.101c-0.693,0.818-1.377,1.636-2.054,2.454   c-0.599,0.724-1.196,1.447-1.782,2.17c-0.634,0.782-1.254,1.563-1.873,2.343c-0.6,0.756-1.2,1.511-1.786,2.266   c-0.558,0.719-1.1,1.435-1.646,2.152c-0.616,0.81-1.237,1.62-1.837,2.426c-0.429,0.577-0.841,1.148-1.262,1.723   c-3.811,5.2-7.293,10.3-10.438,15.199c-0.008,0.012-0.016,0.024-0.023,0.036c-0.828,1.29-1.627,2.561-2.41,3.821   c-0.042,0.068-0.086,0.137-0.128,0.206c-0.784,1.265-1.541,2.508-2.279,3.738c-0.026,0.043-0.053,0.087-0.079,0.13   c-1.984,3.311-3.824,6.503-5.481,9.506C51.412,176.348,47.183,187.347,47,188c-27.432,98.072,58.184,56.657,70.131,50.475   c12.864,6.355,27.346,9.932,42.666,9.932c41.94,0,77.623-26.771,90.905-64.156h-50.68c-7.499,12.669-21.936,21.25-38.522,21.25   c-24.301,0-44-18.412-44-41.125h137.956C255.979,160.308,256.25,156.162,256.25,151.953z M238.232,57.037   c8.306,5.606,14.968,14.41,3.527,44.059c-10.973-17.647-27.482-31.49-47.104-39.099C203.581,57.686,225.686,48.568,238.232,57.037z    M61.716,238.278c-6.765-6.938-7.961-23.836,6.967-54.628c7.534,21.661,22.568,39.811,42,51.33   C101.019,240.299,75.363,252.275,61.716,238.278z M117.287,138c0.771-22.075,19.983-39.75,43.588-39.75   c23.604,0,42.817,17.675,43.588,39.75H117.287z"}]])

(def safari-logo
  [:svg.browser.safari [:path {:d "M81.8,81.8c-17.6,17.6-46.1,17.6-63.6,0 s-17.6-46.1,0-63.6s46.1-17.6,63.6,0S99.4,64.2,81.8,81.8z M44.7,44.7l-23,33.6l33.6-23l23-33.6L44.7,44.7z M78.3,21.7L21.7,78.3 M55.3,55.3L44.7,44.7 M21.7,21.7l5.7,5.7 M34.7,13l3.1,7.4 M50.1,10l-0.1,8 M64.7,12.8l-3.1,7.4 M13,65.3l7.4-3.1 M18,50.1l-8-0.1 M12.8,35.3l7.4,3.1 M72.6,72.6l5.7,5.7 M62.2,79.6l3.1,7.4 M49.9,90l0.1-8 M38.3,79.8l-3.1,7.4 M79.6,37.8l7.4-3.1 M82,49.9l8,0.1 M87.2,64.7l-7.4-3.1"}]])

(def chrome-logo
  [:svg.browser.chrome [:path {:d "M95,50c0,24.9-20.1,45-45,45S5,74.9,5,50 S25.1,5,50,5S95,25.1,95,50z M50,32.5c-9.7,0-17.5,7.8-17.5,17.5S40.3,67.5,50,67.5S67.5,59.7,67.5,50S59.7,32.5,50,32.5z M50,32.5 h41.5 M44.4,94.7l20.7-35.9 M14.1,22.8l20.7,35.9"}]])

(def firefox-logo
  [:svg.browser.firefox [:path {:d "M95,50c0,24.9-20.1,45-45,45 C25.1,95,5,74.8,5,50c0-7,1.6-13.7,4.5-19.6c0-0.2,0-0.5,0-0.7c-0.1-0.8-0.1-1.6-0.1-2.4c0,0-0.1-8.8,4.9-13.4 c0.1,1.6,0.5,3.6,1.2,4.9c1.8,2.8,3.1,3.5,3.8,4c3-1,6.6-1.2,10.8-0.2c0.4-0.4,6.6-6.5,12.3-5.1c-1.9,1.1-5.4,5-6.3,8.9 c0.1,0.2,0.2,0.5,0.4,0.8c0.1,0.2,0.3,0.4,0.4,0.7c0.8,1.1,2.1,2.2,4,2.2c7.2-0.1,7.7,0.5,7.8,1.3c0.1,1.2-0.2,2-0.5,2.6 c-0.4,0.9-1.2,1.7-1.9,2.2c-0.5,0.4-7.1,3.8-7.5,5.1c0.4,3.7-0.2,6.2-0.2,6.2s-0.3-0.4-1.1-0.8c-0.6-0.3-1.9-0.7-2.4-0.9 c-2,0.9-2.5,2.4-2.5,3.4c0,1.3,0.9,4.3,5.4,7c4.7,1.9,9,0,12.4-0.8c4.4-1.1,7.6,1,9,2.5c1.3,1.5,0.2,3.3-1.5,2.9 c-1.7-0.4-3.6,1.2-6.9,3.5c-3.2,2.2-8.8,3.1-14,1.8c13.7,15.1,37.2,11.3,38.3-10.8c0.3,0.7,0.9,1.9,1.1,3.5 c0.9-3.5,2.1-6.3,0.7-19.5c1.3,1.2,3.2,4.6,4.3,6.7c0.7-13.4-6-22.3-10-25.2c2.7,0.2,6.6,2.7,9,5C80,25,79.5,24.1,79,23.3 c-2.1-3.7-5.6-7.7-12.6-12.4c0,0,6.1,0,11.7,3.9c6,4.8,10.7,11.2,13.6,18.4C93.8,38.4,95,44.1,95,50z M72.4,11.1 C65.8,7.1,58.2,5,50,5c-13,0-24.7,5.4-32.9,14.2"}]])

(def integration-data
  {:heroku {:hero {:icon (utils/cdn-path "/img/outer/integrations/heroku-logo.svg")
                   :heading "Deploy to Heroku from CircleCI"
                   :subheading "Experience a simple, modern continuous delivery workflow now."}
            :features [{:type :text
                        :title "Test before you deploy. Always."
                        :icon "circle-success"
                        :text (list
                                [:p
                                 "Heroku revolutionized the way developers think about deployment. "
                                 "Being able to deploy with a simple "
                                 [:code "git push heroku master"]
                                 " is an amazing thing. But setting up a proper continuous delivery "
                                 "workflow means automating every step of the process."]
                                [:p
                                 "With CircleCI "
                                 "whenever you push a commit to master, it will go through a complete "
                                 "continuous delivery pipeline. All of your tests will run with our "
                                 "blazing fast parallelism, and" [:em " only if they pass, "]
                                 "your code will be pushed to Heroku automatically."])}
                       {:type :text
                        :title "Dead Simple Configuration"
                        :icon "setup"
                        :text [:p
                               "The deployment of your application is configured through just a "
                               "few lines of YAML that are kept safe in your source code. All "
                               "you need to do to deploy to Heroku from CircleCI is to "
                               [:a {:href "/docs/continuous-deployment-with-heroku"}
                                "configure your Heroku credentials in our UI, add a simple config file to your project, and push"]
                               ". You can also easily deploy "
                               "different branches to different Heroku apps (e.g. one for staging "
                               "and one for production)."]}
                       {:type :text
                        :title "Watch how to get started in minutes."
                        :icon "play-1"
                        :text [:p
                               "This video shows step-by-step how to configure CircleCI to test "
                               "your application and deploy to Heroku, and how CircleCI keeps "
                               "defects from getting into production. "
                               "See our docs for a "
                               [:a {:href "/docs/continuous-deployment-with-heroku#part-2-multiple-environments"}
                                "followup video"]
                               " showing how to setup a more robust continuous delivery pipeline "
                               "with staging and prod environments."]}
                       {:type :video
                        :title "Continuous deployment with CircleCI and Heroku"
                        :thumbnail (utils/cdn-path "/img/outer/integrations/video-placeholder.svg")
                        :video-id "Hfs_1yuWDf4"}]
            :bottom-header "Ready for world-class continuous delivery?"
            :secondary-cta [:span
                            "Or see our "
                            [:a {:href "/docs/continuous-deployment-with-heroku"}
                             "docs on deploying to Heroku."]]}
   :saucelabs {:hero {:icon (utils/cdn-path "/img/outer/integrations/sauce-labs-logo.svg")
                      :heading "Test with Sauce Labs on CircleCI"
                      :subheading "Test against hundreds of mobile and desktop browsers."}
               :features [{:type :text
                           :title "Selenium WebDriver"
                           :icon "se-1"
                           :text [:p
                                  "Sauce Labs supports automated browser tests using Selenium "
                                  "WebDriver, a widely-adopted browser driving standard. Selenium "
                                  "WebDriver provides a common API for programatically driving "
                                  "browsers implemented in several popular languages, including "
                                  "Java, Python, and Ruby. WebDriver can operate in two modes: "
                                  "local or remote. When run locally, your tests use the Selenium "
                                  "WebDriver library to communicate directly with a browser on the "
                                  "same machine. When run in remote mode, your tests interact with "
                                  "a Selenium Server, and it it is up to the server to drive the "
                                  "browsers. Sauce Labs essentially provides a Selenium Server as a "
                                  "service, with all kinds of browsers available to test. It has "
                                  "some extra goodies like videos of all test runs as well."]}
                          {:type :text
                           :title "All the browsers and platforms you need"
                           :icon "environment"
                           :text [:p
                                  "Sauce Labs provides a huge variety of browsers and operating "
                                  "systems. You can choose between combinations of Firefox, Chrome, "
                                  "Safari, and Internet Explorer browsers and OSX, Windows, and "
                                  "Linux operating systems. You can also test against mobile Safari "
                                  "and Android browsers. Pick whatever browsers are important for "
                                  "you, whether you need to ensure critical functionality works on "
                                  "mobile devices or support old versions of IE. Because Selenium "
                                  "WebDriver provides a unified interface to talk to all of these "
                                  "browsers, you only need to write your browser tests once, and "
                                  "you can run them on as many browsers and platforms as you want."]}
                          {:type :text
                           :title "Test Continuously"
                           :icon "sudo"
                           :text [:p
                                  "CircleCI automatically runs all your tests, against "
                                  "whatever browsers you choose, every time you commit code. You "
                                  "can configure your browser-based tests to run whenever a change "
                                  "is made, before every deployment, or on a certain branch. A "
                                  "Continuous Integration and Delivery workflow with CircleCI and "
                                  "Sauce Labs ensures that browser-specific bugs affecting critical "
                                  "functionality in your app never make it to production."]}
                          {:type :text
                           :title "No public test servers required"
                           :icon "server"
                           :text [:p
                                  "Sauce Labs operates browsers on a network separate from "
                                  "CircleCI build containers, but there needs to be a way for the "
                                  "browsers to access the web application you want to test. The "
                                  "easiest way to do this is to simply run your server during a "
                                  "CircleCI build and use Sauce Connect to setup a secure tunnel "
                                  "between Sauce Labs' browsers and your build containers on "
                                  "CircleCI. There is an in-depth example of this in "
                                  [:a {:href "/docs/browser-testing-with-sauce-labs"} "our docs."]]}]
               :bottom-header "Want to get rid of browser bugs?"
               :secondary-cta [:span "Or see our " [:a {:href "/docs/browser-testing-with-sauce-labs"} "docs on Sauce Labs."]]}})


(defn video-url [video-id]
  (str "https://www.youtube.com/watch?v=" video-id))

(defn video-embed-url [video-id]
  (str "https://www.youtube.com/embed/" video-id "?autohide=1&autoplay=1&modestbranding=1&rel=0&showinfo=0"))


(defmulti feature (fn [_ b] (:type b)))

(defmethod feature :text
  [owner b]
  [:div.feature
   (common/feature-icon (:icon b))
   [:h4.text-center (:title b)]
   (:text b)])

(defmethod feature :video
  [owner b]
  [:div.feature.video
   [:a.play {:href (video-url (:video-id b))
             :on-click (fn [e]
                         (.preventDefault e)
                         (raise! owner [:play-video (:video-id b)]))}
    [:img.thumb {:src (:thumbnail b)}]]
   [:h4.text-center (:title b)]])


(defrender integration [app owner]
  (let [integration-name (get-in app [:navigation-data :integration])]
    (if (= integration-name :docker)
      (om/build docker/docker app)
      (let [integration (get integration-data integration-name)]
        (html [:div.product-page.integrations
               (if-let [video-id (get-in app state/modal-video-id-path)]
                 [:div.modal-overlay {:on-click #(raise! owner [:close-video])}
                  ;; TODO: Once React supports :allowFullscreen (v.0.13.1),
                  ;; replace this hack with the :iframe below. Then remove the
                  ;; `> div > .modal-video` selector in CSS.
                  [:div {:dangerouslySetInnerHTML
                         {:__html (dom/getOuterHtml
                                    (dom/createDom "iframe" #js {:class "modal-video"
                                                                 :src (video-embed-url video-id)
                                                                 :allowFullscreen true}))}}]
                  ; [:iframe.modal-video {:src (video-embed-url video-id)
                  ;                       :allowFullscreen true}]
                  [:button.close {:aria-label "Close"
                                  :on-click #(raise! owner [:close-video])}]])
               (let [hero (:hero integration)]
                 [:div.jumbotron
                  common/language-background-jumbotron
                  [:section.container
                   [:div.row
                    [:div.hero-title.center-block
                     [:div.text-center
                      (if-let [icon-src (:icon hero)]
                        [:img.hero-logo {:src icon-src}])]
                     [:h1.text-center (:heading hero)]
                     [:h3.text-center (:subheading hero)]]]]
                  [:div.row.text-center
                   (om/build common/sign-up-cta {:source (str "integrations/" (name integration-name))})]])

               [:div.outer-section
                [:section.container
                 (for [row (partition-all 2 (:features integration))]
                   [:div.feature-row
                    (for [b row]
                      (feature owner b))])]]

               [:div.outer-section.outer-section-condensed
                common/language-background
                [:section.container
                 [:div.col-xs-12
                  [:h2.text-center "Ready for world-class continuous delivery?"]
                  [:p.text-center (:secondary-cta integration)]
                  [:div.text-center
                   (om/build common/sign-up-cta {:source (str "integrations/" (name integration-name))})]]]]])))))
