(ns frontend.components.stories
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.features :as features]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(def shopify-logo
  [:svg.shopify-logo
   {:viewBox "0 0 55 70"}
   [:path.logomark {:d "M37.5,13l-1.9,0.6c-0.8-2.5-2-4.2-3.4-5.2c-1.1-0.7-2.2-1.1-3.5-1c-0.3-0.3-0.5-0.6-0.9-0.9 c-1.4-1.1-3.2-1.3-5.3-0.5c-6.4,2.3-9.1,10.6-10.1,14.8l-5.6,1.7c0,0-1.3,0.4-1.6,0.7c-0.3,0.4-0.4,1.5-0.4,1.5L0,61.3L35.6,68 l2.7-55C37.9,12.9,37.5,13,37.5,13z M28.4,15.8l-6.2,1.9c0.8-3.2,2.4-6.5,5.4-7.8C28.3,11.6,28.4,13.8,28.4,15.8z M23.2,8 c1.3-0.5,2.3-0.5,3.1,0.1c-4,1.8-5.8,6.5-6.6,10.4L14.8,20C15.9,16,18.4,9.8,23.2,8z M26.7,34.9c-0.3-0.1-0.6-0.3-1-0.4 c-0.4-0.1-0.8-0.3-1.2-0.4C24,34,23.5,33.9,23,33.9c-0.5-0.1-1-0.1-1.6,0c-0.5,0-1,0.1-1.4,0.3c-0.4,0.1-0.7,0.3-1,0.6 c-0.3,0.2-0.5,0.5-0.7,0.9c-0.2,0.3-0.2,0.7-0.3,1.1c0,0.3,0,0.6,0.1,0.9c0.1,0.3,0.3,0.6,0.5,0.8c0.2,0.3,0.5,0.6,0.9,0.8 c0.4,0.3,0.8,0.6,1.3,0.9c0.7,0.4,1.4,0.9,2,1.5c0.7,0.6,1.3,1.2,1.9,2c0.6,0.7,1,1.6,1.3,2.5c0.3,0.9,0.5,2,0.4,3.1 c-0.1,1.9-0.5,3.5-1.2,4.8c-0.7,1.3-1.6,2.4-2.7,3.1c-1.1,0.7-2.3,1.2-3.7,1.4c-1.3,0.2-2.8,0.1-4.2-0.2c0,0,0,0,0,0c0,0,0,0,0,0 c0,0,0,0,0,0c0,0,0,0,0,0c-0.7-0.2-1.4-0.4-2-0.6c-0.6-0.2-1.2-0.5-1.7-0.8c-0.5-0.3-1-0.6-1.4-1c-0.4-0.3-0.7-0.7-1-1l1.6-5.4 c0.3,0.2,0.6,0.5,1,0.8c0.4,0.3,0.8,0.6,1.3,0.8c0.5,0.3,1,0.5,1.5,0.7c0.5,0.2,1.1,0.4,1.6,0.4c0.5,0.1,0.9,0.1,1.3,0 c0.4-0.1,0.7-0.2,0.9-0.4c0.3-0.2,0.5-0.5,0.6-0.8c0.1-0.3,0.2-0.7,0.2-1c0-0.4,0-0.7-0.1-1.1c-0.1-0.3-0.2-0.7-0.5-1 c-0.2-0.3-0.5-0.7-0.9-1c-0.4-0.3-0.8-0.7-1.3-1.1c-0.6-0.5-1.2-1-1.7-1.5c-0.5-0.5-1-1.1-1.3-1.8c-0.4-0.6-0.7-1.3-0.9-2 c-0.2-0.7-0.3-1.5-0.2-2.4c0.1-1.4,0.4-2.8,0.8-4c0.5-1.2,1.2-2.3,2.1-3.2c0.9-1,2-1.8,3.2-2.4c1.3-0.6,2.7-1.1,4.4-1.3 c0.8-0.1,1.5-0.1,2.2-0.1c0.7,0,1.3,0,1.9,0.1c0.6,0.1,1.2,0.2,1.7,0.3c0.5,0.1,0.9,0.3,1.3,0.5L26.7,34.9z M30.5,15.1 c0-0.2,0-0.5,0-0.7c-0.1-1.9-0.3-3.5-0.8-4.9c0.5,0,0.9,0.2,1.3,0.5c1.1,0.8,1.9,2.4,2.5,4.2L30.5,15.1z M55.7,63.8L36.5,68l2.7-55 c0.2,0,0.3,0.1,0.5,0.2l3.7,3.7l5,0.4c0,0,0.2,0,0.3,0.1c0.2,0.1,0.2,0.4,0.2,0.4L55.7,63.8z",
                    :fill "#7bb460"}]])

(defn shopify [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:article.product-page.stories.shopify
        [:div.jumbotron
         common/language-background-jumbotron
         [:section.container
          [:div.row
           [:div.hero-title.center-block
            [:div.text-center
             shopify-logo]
            [:h1.text-center "Shopify’s CI, with Circle CI."]
            [:h3.text-center
             "“We were able to rapidly grow our team and code base without fear of outgrowing CircleCI.”"
             [:br]
             "John, Director of Engineering, Shopify"]]]]]

        [:div.outer-section
         [:section.container
          [:div.stories-stats
           [:dl
            [:dt "Developers"] [:dd "1,300"]
            [:dt "Funding"]    [:dd "122m"]
            [:dt "Technology"] [:dd "Ruby"]
            [:dt "Past Setup"] [:dd "Internal"]]]]

         [:section.container
          [:h2 "Background"]
          [:p "Shopify has a simple goal: to make commerce better. They do this by making it easy for over 100,000 online and brick and mortar retailers to accept payments. They've experienced tremendous growth over the last several years and in order to serve their growing customer base they've had to double their engineering team from 60 to 130 in the last year alone. In addition to the usual growth challenges, they faced the problem of maintaining their test-driven and continuous deployment culture while keeping productivity high."]
          [:p "Shopify has always taken Continuous Integration (CI) and Continuous Deployment (CD) seriously and built an in-house tool early on to make both practices an integrated part of their workflow. As the team grew, this in-house tool required more and more work to maintain and the self-hosted server was struggling to handle the load."]
          [:p "Eventually the Shopify team was dedicating the equivalent of two full-time engineers to maintaining their CI tool, and even then it was not performing up to their needs. Custom configuration was required to add a new repository, it was difficult to integrate with other tools, and the test suite took too long. In the spring of 2013 freeing up developers to focus on the core product, creating a more streamlined and flexible developer workflow, and getting new products to market quickly were all high priorities for John Duff, Shopify's Director of Engineering. Much of this, he decided, could be accomplished with a better CI solution."]

          [:h2 "Data Analysis"]
          [:p "In looking for a CI solution, Shopify needed something that was dependable, affordable, and would meet three core product criteria."]
          [:p "Several of the developers at Shopify had used CircleCI before and recommended it as potentially meeting all of these needs. In order to try out CircleCI several developers signed up for a 14-day free trial using their GitHub accounts and followed a couple of their Shopify repositories from within the CircleCI app. CircleCI then inferred their test environment based on existing code and their tests began to run automatically. The developers were able to get quick clarification on a few setup details via CircleCI's live chat support room, and within a few minutes they were convinced that CircleCI would meet and surpass their needs."]
          [:p "After using the product for a few days there were several features that set CircleCI apart from the others; easy scalability of both build containers and parallelism, a well documented REST API, and extensive customization and configurability options including SSH access to the build machines. This, combined with the easy setup and helpful support, convinced the team that CircleCI was the perfect solution."]

          [:div.row
           [:div.feature
            (common/feature-icon "scale")
            [:h3 "Powerful Scalability"]
            [:p "Shopify was undergoing rapid growth and they needed a solution that they would not outgrow."]]
           [:div.feature
            (common/feature-icon "api")
            [:h3 "Robust API"]
            [:p "Shopify required a robust API that would allow integration into their existing workflow and tools."]]
           [:div.feature
            (common/feature-icon "settings")
            [:h3 "Easy Customization"]
            [:p "Shopify needed the ability to easily configure, customize, and debug build their machines."]]]]]

        [:div.outer-section.outer-section-condensed
         [:section.container
          [:div.row
           [:div.col-xs-6.col-xs-offset-3
            (features/testimonial {:company-name "Shopify"
                                   :company-short "shopify"
                                   :customer-quote "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customer, not maintaining CI infrasturcture."
                                   :employee-name "John Duff"
                                   :employee-title "Director of Engineering"})]]]]

        [:div.outer-section
         [:section.container
          [:h2 "Implementation"]
          [:p "CircleCI integrates natively with GitHub, which Shopify was already using, so set up time was minimal; it only took a few minutes to follow the rest of their repos on CircleCI and to invite the rest of their team members. Once their tests were running, they started optimizing their containers and parallelization from within the CircleCI app so that their test suite would run as quickly as possible. Once they had the tests running for all their projects, the next step was setting up Continuous Deployment."]
          [:p "CD has always been a core part of the engineering culture at Shopify, so getting deployment set up with CircleCI was essential. To streamline their CD process, Shopify used the CircleCI API to build a custom 'Ship It' tool that allows any developer to deploy with the press of a button, as long as they have a green build on CircleCI. All they had to do to build this was verify that the pull request in question returned \"outcome\" : \"success\" from the CircleCI API after merging with master, and then allow the developer to deploy."]
          [:p "This same functionality can also be accomplished without using the API by putting the deployment script directly into the circle.yml file."]
          shared/stories-procedure]]

        [:div.outer-section.outer-section-condensed
         common/language-background
         [:section.container
          [:div.col-xs-12
           [:h2.text-center "Ready for world-class continuous delivery?"]
           [:p.text-center [:span
                            "Or see our "
                            [:a {:href "/docs/continuous-deployment-with-heroku"}
                             "docs on deploying to Heroku."]]]
           [:div.text-center
            (common/sign-up-cta owner "stories/shopify")]]]]]))))
