(ns frontend.components.docker
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [goog.string :as gstring]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.features :as features]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defrender docker-diagram [app owner]
  (let [diagram-structure [["github" "CircleCI checks out your code from GitHub."]
                           ["docker" "Docker base images, or images for any dependent services can be pulled from any Docker registry."]
                           ["circle" "CircleCI builds, runs, and tests Docker images in any configuration."]
                           ["docker" "When all tests pass, built Docker images are pushed to the registry."]
                           ["scale" "CircleCI can trigger a deployment of new images to any Docker host with an API. Images built on CircleCI are pulled from the registry into production."]]
        active-section-index (get (om/get-state owner) :selected-index 0)
        diagram-elem :img.diagram-icon]
    (html
      [:div.docker-diagram
       [:div.diagram-content
        (map-indexed
         (fn [index [icon-name explanation]]
           [:div.diagram-section
            (if (not= index 0)
              [:span.connector-line {:class (if (= index (+ 1 active-section-index)) "active")}])
            [:a.diagram-icon
             {:on-click #(om/set-state! owner :selected-index index)}
             [:img {:src (utils/cdn-path
                           (if (= index active-section-index)
                             (gstring/format "/img/outer/docker/diagram-%s.svg" icon-name)
                             (gstring/format "/img/outer/docker/diagram-%s-grey.svg" icon-name)))}]]
            (if (< index (- (count diagram-structure) 1))
              [:span.connector-line {:class (if (= index active-section-index) "active")}])
            (if (= index active-section-index)
              [:span.active-indicator-line])
            (if (= index active-section-index)
              [:img.active-indicator-dot {:src (utils/cdn-path "/img/outer/docker/diagram-dot.svg")}])])
        diagram-structure)]
       [:div.diagram-annotation
        [:div.col-xs-3.text-right
         (if (> active-section-index 0)
           [:a
            {:on-click #(raise! owner [:docker-diagram-index-selected (- active-section-index 1)])}
            [:img.diagram-arrow {:src (utils/cdn-path "/img/outer/docker/diagram-arrow-left.svg")}]])]
        [:div.col-xs-6.text-center
         [:h3
          (last (nth diagram-structure active-section-index))]]
        [:div.col-xs-3.text-left
         (if (< active-section-index (- (count diagram-structure) 1))
           [:a
            {:on-click #(raise! owner [:docker-diagram-index-selected (+ active-section-index 1)])}
            [:img.diagram-arrow {:src (utils/cdn-path "/img/outer/docker/diagram-arrow-right.svg")}]])]]])))

(defrender docker-cta [source owner]
  (html
    [:div.outer-section.outer-section-condensed
     [:section.container-fluid
      [:img.background.docker-banner {:src (utils/cdn-path "/img/outer/integrations/banner-docker-logo.svg")}]
      [:div.docker-cta
       [:div.cta-text
        [:h3.text-center "Start building with your Docker containers today!"]]
       [:div.cta-btn
        (common/sign-up-cta owner source)]]]]))


(defn docker [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.product-page.integrations.docker
        [:div.jumbotron
         [:img.background.main {:src (utils/cdn-path "/img/outer/integrations/main-docker-logo.svg")}]
         [:h1 "A modern continuous delivery process for your Docker applications."]
         [:h3.small-aside "CircleCI can support any Docker-based build, test, and deployment workflow. With complete flexibility to run any Docker commands and access public or private registries, you can ship modern applications faster and more reliably than ever before."]
         (common/sign-up-cta owner "docker")
         ]
        [:div.outer-section
         [:section.container
          [:h2.text-center "A better way to build and deploy applications"]
          (om/build docker-diagram app)
          ]]
        [:div.outer-section.outer-section-condensed
         [:section.container
          [:div.row
           [:div.col-xs-6.col-xs-offset-3
            (features/testimonial {:company-name "Bleacher Report"
                                   :company-short "br"
                                   :customer-quote "With just one line of code you have access to all of the power of Docker."
                                   :employee-name "Felix Rodriguez"
                                   :employee-title "DevOps Engineer"})
         ]]]]
        [:div.outer-section
         [:section.container
          [:div.row
           [:div.outer-section-overview
            [:h2.text-center "Why Docker?"]
            [:p.text-center "We've all heard a lot about Docker– why exactly is everyone so excited?"]]]
          [:div.row
           [:div.feature
            (common/feature-icon "sudo")
            [:h3 "Dev-test-production equivalence"]
            [:p "Any containers you use locally as part of your dev environment, as well as any containerized services that will be running in production, can also be run in your test environment on CircleCI. Just " [:code "docker pull"] " in anything you need."]]
           [:div.feature
            (common/feature-icon "deploy-1")
            [:h3 "Deploy any application as a single binary artifact"]
            [:p "Don't you love the thrill of making sure you have a robust set of scripts and recipes to setup the same exact same Nginx, uwsgi, and Django configuration on every production server? Of course you don't! Docker lets you specify everything from your Linux distro to config files to what executables run at startup in a Dockerfile, build all of that information into a Docker image, test it, and deploy the exact same image byte-for-byte to production."]]
           [:div.feature
            (common/feature-icon "scale")
            [:h3 "Efficient OS-level containerization"]
            [:p "Dividing infrastructure into many little VMs wastes a lot of computing resources by over-allocating CPU, RAM, and Disk capacity to each VM. On the other hand, higher-level, PaaS-style encapsulation is often too restrictive for application and service developers. Docker's model takes advantage of Linux kernel features to achieve lightweight isolation between containers. This allows for much more efficient utilization of computing resources, giving you more bang for your hardware buck."]]
           ]]]
        [:div.outer-section
         [:section.container
          [:div.row
           [:div.outer-section-overview
            [:h2.text-center "Using Docker on CircleCI"]
            [:p.text-center "Here are just a few of the things you can do with Docker on CircleCI."]]]
          [:div.row
           [:div.feature
            (common/feature-icon "artifacts")
            [:h3 "Public and private registry support"]
            [:p
             "You can use the "
             [:code "docker pull"]
             " and "
             [:code "docker push"]
             " commands as usual on CircleCI, meaning that you can interact with any Docker registry server."]]
           [:div.feature
            (common/feature-icon "environment")
            [:h3 "Easy integration tests for a microservice architecture"]
            [:p "You can run as many Docker containers as you like on CircleCI. If your production application consists of three interconnected services, you can pull them all down into a CircleCI container, link them together, and test them together."]]
           [:div.feature
            (common/feature-icon "docker")
            [:h3 "Continuous Delivery of your Docker images"]
            [:p "Once you have built an image and optionally pushed it to a registry, CircleCI makes it easy to deploy applications to AWS Elastic Beanstalk, Google Container Engine, CoreOS, Docker Swarm or any other host that can run Docker containers."]]]]]

        (om/build docker-cta "docker")]))))
