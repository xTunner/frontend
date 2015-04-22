(ns frontend.components.stories
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.docker :as docker]
            [frontend.components.features :as features]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender defrendermethod html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn story-jumbotron [{:keys [logo heading subheading]}]
  [:div.jumbotron
   common/language-background-jumbotron
   [:section.container
    [:div.row
     [:div.hero-title.center-block
      [:div.text-center
       (when logo
         [:img.hero-logo {:src logo}])]
      [:h1.text-center heading]
      [:h3.text-center subheading]]]]])

(defrender story-cta [story-name owner]
  (html
    [:div.outer-section.outer-section-condensed.bottom-cta-section
     common/language-background
     [:section.container
      [:div.col-xs-12
       [:h2.text-center "Ready for world-class continuous delivery?"]
       [:div.text-center
        (common/sign-up-cta owner (str "stories/" story-name))]]]]))


(defmulti story
  (fn [app owner]
    (get-in app [:navigation-data :story])))

(defrendermethod story :shopify
  [app owner]
  (html
    [:article.product-page.stories.shopify
     (story-jumbotron {:logo (utils/cdn-path "/img/outer/customers/logo-shopify.svg")
                       :heading "Scalable Continuous Integration with CircleCI"
                       :subheading (list
                                     "“We were able to rapidly grow our team and code base without fear of outgrowing CircleCI.”"
                                     [:br]
                                     "John Duff, Director of Engineering, Shopify")})
     [:div.outer-section
      [:section.container
       [:div.row
        [:div.fact.col-xs-3
         [:h3.header "Developers"]
         [:p.value "130"]]
        [:div.fact.col-xs-3
         [:h3.header "Funding"]
         [:p.value "122m"]]
        [:div.fact.col-xs-3
         [:h3.header "Technology"]
         [:p.value "Ruby"]]
        [:div.fact.col-xs-3
         [:h3.header "Past Setup"]
         [:p.value "Internal"]]]]

      [:section.container
       [:h2 "Background"]
       [:p "Shopify has a simple goal: to make commerce better. They do this by making it easy for over 100,000 online and brick and mortar retailers to accept payments. They've experienced tremendous growth over the last several years and in order to serve their growing customer base they've had to double their engineering team from 60 to 130 in the last year alone. In addition to the usual growth challenges, they faced the problem of maintaining their test-driven and continuous deployment culture while keeping productivity high."]
       [:p "Shopify has always taken Continuous Integration (CI) and Continuous Deployment (CD) seriously and built an in-house tool early on to make both practices an integrated part of their workflow. As the team grew, this in-house tool required more and more work to maintain and the self-hosted server was struggling to handle the load."]
       [:p "Eventually the Shopify team was dedicating the equivalent of two full-time engineers to maintaining their CI tool, and even then it was not performing up to their needs. Custom configuration was required to add a new repository, it was difficult to integrate with other tools, and the test suite took too long. In the spring of 2013 freeing up developers to focus on the core product, creating a more streamlined and flexible developer workflow, and getting new products to market quickly were all high priorities for John Duff, Shopify's Director of Engineering. Much of this, he decided, could be accomplished with a better CI solution."]

       [:h2 "Data Analysis"]
       [:p "In looking for a CI solution, Shopify needed something that was dependable, affordable, and would meet three core product criteria."]
       [:p "Several of the developers at Shopify had used CircleCI before and recommended it as potentially meeting all of these needs. In order to try out CircleCI several developers signed up for a 14-day free trial using their GitHub accounts and followed a couple of their Shopify repositories from within the CircleCI app. CircleCI then inferred their test environment based on existing code and their tests began to run automatically. The developers were able to get quick clarification on a few setup details via CircleCI's live chat support room, and within a few minutes they were convinced that CircleCI would meet and surpass their needs."]
       [:p "After using the product for a few days there were several features that set CircleCI apart from the others; easy scalability of both build containers and parallelism, a well documented REST API, and extensive customization and configurability options including SSH access to the build machines. This, combined with the easy setup and helpful support, convinced the team that CircleCI was the perfect solution."]

       [:div.feature-row
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
                                :customer-quote "One of my favorite things about CircleCI is that their team really cares about making sure their customers get maximum value out of their product."
                                :employee-name "Arthur Neves"
                                :employee-title "Developer"
                                :read-the-story? false})]]]]

     [:div.outer-section
      [:section.container
       [:h2 "Implementation"]
       [:p "CircleCI integrates natively with GitHub, which Shopify was already using, so set up time was minimal; it only took a few minutes to follow the rest of their repos on CircleCI and to invite the rest of their team members. Once their tests were running, they started optimizing their containers and parallelization from within the CircleCI app so that their test suite would run as quickly as possible. Once they had the tests running for all their projects, the next step was setting up Continuous Deployment."]
       [:p "CD has always been a core part of the engineering culture at Shopify, so getting deployment set up with CircleCI was essential. To streamline their CD process, Shopify used the CircleCI API to build a custom 'Ship It' tool that allows any developer to deploy with the press of a button, as long as they have a green build on CircleCI. All they had to do to build this was verify that the pull request in question returned \"outcome\" : \"success\" from the CircleCI API after merging with master, and then allow the developer to deploy."]
       [:p "This same functionality can also be accomplished without using the API by putting the deployment script directly into the circle.yml file."]

       shared/stories-procedure

       [:h2 "Results"]
       [:p "Today, 1 year after initially switching to CircleCI, Shopify has scaled their engineering team to 130 team members who on average merge 300 pull requests and deploy 100 times per week. Thanks to CircleCI, they've managed to maintain their agile and efficient development process, with new projects being added effortlessly and everyone working off of a master branch (rather than having to maintain production and development branches). Their test suite runs faster than it ever did with their previous solution, and now that developers don't have to run tests on their local machine they can work on other projects while CircleCI runs their tests in the background. Shopify also uses CircleCI along with Capistrano to continuously deploy their application for anything from a small bug fix, to a package upgrade, to a new feature."]]]

     [:div.outer-section.outer-section-condensed
      [:section.container
       [:div.row
        [:div.col-xs-6.col-xs-offset-3
         (features/testimonial {:company-name "Shopify"
                                :company-short "shopify"
                                :customer-quote "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customer, not maintaining CI infrasturcture."
                                :employee-name "John Duff"
                                :employee-title "Director of Engineering"
                                :read-the-story? false})]]]]

     [:div.outer-section
      [:section.container
       [:p "The Shopify team no longer has to worry about scaling their testing infrastructure, maintaining their test stack, or monitoring their deployments. They focus on building products that bring value to their customers while relying on CircleCI to ensure that they are able to get those products to market quickly and reliably."]]]

     (om/build story-cta "shopify")]))

(defrendermethod story :wit
  [app owner]
  (html
    [:article.product-page.stories.shopify
     (story-jumbotron {:logo (utils/cdn-path "/img/outer/customers/customer-wit.svg")
                       :heading "Continuous Delivery for Containers"
                       :subheading "How Wit.ai uses CircleCI and Docker to deploy their containerized services"})

     [:div.outer-section
      [:section.container
       [:h2 "Background"]
       [:p "Wit.ai, which was recently acquired by Facebook, makes advanced natural language processing tools for developers. As a service that others rely on for their applications to function, it's vital for Wit.ai to ensure that every service deployed is rigorously tested, and that there are no unknown quantities in their delivery process."]

       [:h2 "Problem"]
       [:p "Wit.ai's platform is built with services written in Clojure, including user-facing web apps and service endpoints, as well as a Riak-backed Datomic database. As Wti.ai was scaling their platform, they ran in to a few different issues. First, different services were built, tested, and deployed in different ways. The development team was moving fast and wanted to deploy multiple times per day, but were slowed down by this operational complexity. Second, it was difficult to ensure that their production environment was always consistent with their development and test environments. Making sure that the proper binary artifacts, server OS, and configuration files were in place required additional tooling which further added to their issues around complexity."]]]

     [:div.outer-section.outer-section-condensed
      [:section.container
       [:div.row
        [:div.col-xs-6.col-xs-offset-3
         (features/testimonial {:company-name "Facebook (formerly Wit.ai)"
                                :company-short "wit"
                                :customer-quote "We no longer have to build images locally and manually push them to Docker Hub. Our builds are more consistent and our deployment process is now easy and safe enough for anyone in the company to deploy to production."
                                :employee-name "Oliv Vaussy"
                                :employee-title "Software Developer"
                                :read-the-story? false})]]]]

     [:div.outer-section
      [:section.container
       [:h2 "Implementation"]
       [:p "The first step the Wi.ai team took to solve these problems was to implement Docker. All of their services, from their web app to speech recognition APIs to Datomic transactors, were containerized with Docker in order to achieve development-production parity, minimize downtime, and simplify their deployment process."]
       [:p "Next, Wit.ai begain searching for a Continuous Integration and Delivery workflow that would allow any team member to easily build and deploy a Docker image. This would help them move quickly and deliver value to their customers as soon as the code had been written and tested. Wit.ai ultimately settled on CircleCI because it gave them the ability to run Docker natively from within their build containers, which would let them easily build and deploy images from within their CI environment."]
       [:p "Wit.ai's current development and deployment workflow looks like this:"]

       [:ul
        [:li "Each time a developer pushes code to GitHub, CircleCI checks out the project and builds the Docker image."]
        [:li "CircleCI also pulls down images for all the other services that are necessary to run integration tests on the service being developed from their Docker registry."]
        [:li "CircleCI then runs all of these containers inside of a CircleCI build container, and runs the test suite."]
        [:li "After the tests pass, CircleCI pushes the images to their Docker registry, from which images are deployed to their CoreOS fleet running on AWS EC2."]]

       [:p "With this setup, Wit.ai's deployment process is straightforward, automated, and safe."]

       (om/build docker/docker-diagram app)]]

     (om/build story-cta "wit")]))
