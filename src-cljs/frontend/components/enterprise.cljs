(ns frontend.components.enterprise
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.analytics.mixpanel :as mixpanel]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.contact-form :as contact-form]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [goog.string :as gstr]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html inspect]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn modal [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div#enterpriseModal.fade.hide.modal
        [:div.modal-body
         [:h4
          "Contact us to learn more about enterprise Continuous Delivery"]
         [:hr]
         (om/build shared/contact-form app {:opts {:enterprise? true}})]]))))

(defn arrow [name]
  [:img.arrow {:class name
               :src (utils/cdn-path (str "/img/outer/enterprise/arrow-" name ".svg"))}])

(defn language [name]
  [:img.background.language {:class name
                             :src (utils/cdn-path (str "/img/outer/languages/language-" name ".svg"))}])


(def contact-form
  (contact-form/contact-form-builder
   {:id "contact-form"
    :class "form-horizontal"
    :action "/about/contact"}
   {:params-filter (fn [{:strs [name email company phone developer-count]}]
                     {:name name
                      :email email
                      :message (->> {"Company" company
                                     "Phone" phone
                                     "Developer count" developer-count
                                     "Form URL" js/location.href
                                     "Initial referrer" (mixpanel/get-property "$initial_referrer")
                                     "UTM medium" (mixpanel/get-property "utm_medium")
                                     "UTM source" (mixpanel/get-property "utm_source")
                                     "UTM campaign" (mixpanel/get-property "utm_campaign")
                                     "UTM content" (mixpanel/get-property "utm_content")
                                     "UTM term" (mixpanel/get-property "utm_term")}
                                    (map (fn [[k v]] (gstr/format "%s: %s" k (or v "not set"))))
                                    (str/join "\n"))
                      :enterprise true})
    :success-hook (fn [{:keys [email]}]
                    (mixpanel/track "enterprise_form_submitted"
                                    {:url js/location.href
                                     :email email}))}
   (fn [control notice form-state]
     (list
      [:div.row.contact-form
       [:div.col-sm-8.col-sm-offset-2
        [:div.row
         [:div.col-sm-6
          (control :input.input-lg
                   {:type "text"
                    :name "company"
                    :required true
                    :disabled (= :loading form-state)
                    :placeholder "Company"})]
         [:div.col-sm-6
          (control :input.input-lg.blue-focus-border
                   {:type "text"
                    :name "phone"
                    :disabled (= :loading form-state)
                    :placeholder "Phone"})]]
        [:div.row
         [:div.col-sm-6
          (control :input.input-lg
                   {:type "text"
                    :name "name"
                    :required true
                    :disabled (= :loading form-state)
                    :placeholder "Name"})]
         [:div.col-sm-6
          (control :input.input-lg
                   {:type "text"
                    :name "developer-count"
                    :required true
                    :disabled (= :loading form-state)
                    :placeholder "# of Developers"})]]
        [:div.row
         [:div.col-sm-6
          (control :input.input-lg
                   {:type "email"
                    :name "email"
                    :required true
                    :disabled (= :loading form-state)
                    :placeholder "Email"})]
         [:div.col-sm-6
          [:div.telephone-info
           "Or call "
           [:a.telephone-number {:href "tel:+14158515247"} "415.851.5247"]
           " for an Enterprise quote."]]]]]
      (om/build contact-form/transitionable-height
                {:class "notice"
                 :children (html
                            (when notice
                              [:div {:class (:type notice)}
                               (:message notice)]))})
      [:div.row
       [:div.col-xs-12.text-center
        (om/build contact-form/morphing-button {:text "Get More Info" :form-state form-state})
        [:div.success-message
         {:class (when (= :success form-state) "success")}
         "Thank you for submitting your information."
         [:br]
         "Someone from our Enterprise team will contact you within one business day."]]]))))

(def enterprise-bg
  (map arrow ["left-a-1"
              "left-a-2"
              "left-a-3"
              "left-a-4"
              "right-a-1"
              "right-a-2"
              "right-a-3"
              "right-a-4"]))

(defn enterprise [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div#enterprise
        [:div.jumbotron
         enterprise-bg
         [:section.container
          [:div.row
           [:article.hero-title.center-block
            [:div.text-center
             [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
            [:h1.text-center "Ship code at the speed of business."]
            [:h3.text-center "The same Continuous Integration and Deployment platform that developers love, with added security for the enterprise. CircleCI Enterprise lets you quickly and securely build, test, and deploy your applications."]]]

          [:div.row.text-center
           [:a.btn.btn-cta {:href "#contact-form"} "Get More Info"]]]]
        ;; need this wrapper for border-top to span the full screen
        [:div.outer-section
         [:div.container
          [:section.row
           [:div.col-xs-4
            [:article
             (common/feature-icon "circle")
             [:h2.text-center "Ship Faster"]
             [:p "The same Continuous Integration and Deployment platform that developers love, with added security for the enterprise. CircleCI Enterprise lets you quickly and securely build, test, and deploy your applications."]]
            ]
           [:div.col-xs-4
            [:article
             (common/feature-icon "security")
             [:h2.text-center "World Class Security"]
             [:p
              "You can run CircleCI Enterprise in your own private cloud or in ours, allowing you to maintain scalability while achieving enterprise level security."]]]
           [:div.col-xs-4
            [:article
             (common/feature-icon "time")
             [:h2.text-center "Focus on What Matters"]
             [:p
              "Time is a valuable resource, so you should focus on what moves the needle for your business. CircleCI removes the pain of managing build machines and scaling your build fleet, allowing developers to focus on what matters."]]]]]]
         [:div.outer-section
          [:div.container
           [:section.row
            [:div.col-xs-8.col-xs-offset-2.enterprise-integrations
             [:h2.text-center "Integrations"]
             [:p "CircleCI is built for unlimited flexibility. From hosting options to test frameworks or programming frameworks, we let you use the technology you need. GitHub Enterprise, Docker, SauceLabs, and many more."]]]
           [:section.row
            [:div.col-xs-4
             [:div.integration-logo.github
              [:img {:src (utils/cdn-path "/img/outer/enterprise/integration-github-1.svg")}]]
             [:div.integration-text
              "Enjoy all of the benefits of CircleCI's rich GitHub integration with your own GitHub Enterprise instance. Authenticate against GitHub Enterprise, see the status of CircleCI builds from your PR pages on GitHub Enterprise, and easily navigate to specific GitHub PRs and commits from CircleCI build pages."
              ;; TODO: add integrations page for GitHub
              ; [:a.integration-learn-more {:href "/integrations"} "Learn more"]
              ]]
            [:div.col-xs-4
             [:div.integration-logo.docker
              [:img {:src (utils/cdn-path "/img/outer/enterprise/integration-docker-1.svg")}]]
             [:div.integration-text
              "CircleCI Enterprise supports all of the container-oriented features Docker has to offer. Pull down base images from any registry, build your own Dockerfiles right in CircleCI, link containers together and run integration tests, and push built Docker images to production - all driven by CircleCI's simple yaml-based configuration and intuitive UI."
              [:a.integration-learn-more {:href "/integrations/docker"} "Learn more"]]]
            [:div.col-xs-4
             [:div.integration-logo.sauce-labs
              [:img {:src (utils/cdn-path "/img/outer/enterprise/integration-sauce-1.png")}]]
             [:div.integration-text
              "Test against any version of any browser with CircleCI's Enterprise SauceLabs integration. Using the Sauce Connect tunnel, you can even test applications running securely within CircleCI build containers behind your firewall. Automate your cross-browser and mobile testing."
              ;; TODO: add integrations page for Sauce Labs
              [:a.integration-learn-more {:href "/integrations/saucelabs"} "Learn more"]]]]]]
        [:section.outer-section.outer-section-condensed
          (map language ["rails-1"
                         "clojure-1"
                         "java-1"
                         "php-1"])
         [:div.container
          [:div.row
           [:div.col-xs-8.col-xs-offset-2
            [:div.testimonial
             [:img.customer-header {:src (utils/cdn-path "/img/outer/customers/customer-shopify.svg")
                                    :alt "Shopify"}]
             [:div.customer-quote {:class "quote-shopify"}
              [:blockquote "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customers, not maintaining CI Infrastructure."]]
             [:div.customer-citation
              [:div.customer-employee-name "John Duff"]
              [:div
               "Director of Engineering at "
               [:span.customer-company-name "Shopify"]
               " – "
               [:a.customer-story {:href "/stories/shopify"}
                "Read the story"]]]]]]]]

        [:div.outer-section
         [:section.container
          (common/feature-icon "phone")
          [:h2.text-center "Learn More About CircleCI Enterprise"]
          [:div.enterprise-cta-contact
           (om/build contact-form nil)]]]]))))

(defrender enterprise-azure [app owner]
  (html [:div#enterprise
         [:div.jumbotron
          enterprise-bg
          [:section.container
           [:div.row
            [:article.hero-title.center-block
             [:h1.text-center "Next-Generation CI and CD on"
              [:img.inline-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-windows.png")}]]
             [:h3.text-center "Access all of the functionality of CircleCI with all of the security and control of your own Virtual Network."]]]

           [:div.row.text-center
            [:a.btn.btn-cta {:href "#contact-form"} "Get More Info"]]]]
         [:div.outer-section
          [:div.container
           [:section.row
            [:div.col-xs-6
             [:article
              (common/feature-icon "github")
              [:h2.text-center "Works with GitHub Enterprise"]
              [:p.text-center "CircleCI has integrated seamlessly with GitHub from the very beginning, and CircleCI Enterprise is no different. Sign in with GitHub Enterprise, view build status right from PR pages, and quickly navigate from builds on CircleCI to corresponding commits and PRs on GitHub Enterprise."]]]
            [:div.col-xs-6
             [:article
              (common/feature-icon "key-hole")
              [:h2.text-center "Sign in with Active Directory"]
              [:p.text-center "Building on GiHub Enterprise's first-class support for Active Directory authentication, you can now manage access to your own CircleCI installation with AD or other LDAP or SAML-based services."]]]]
           [:section.row.extra-row
            [:div.col-xs-6
             [:article
              (common/feature-icon "circle")
              [:h2.text-center "We come to you"]
              [:p.text-center "Run your own CircleCI installation together with your existing Azure infrastructure. You've invested time, money, and people in your cloud infrastructure. CircleCI Enterprise on Azure makes those investments pay dividends by integrating seemlessly with your existing setup."]]]
            [:div.col-xs-6
             [:article
              (common/feature-icon "security")
              [:h2.text-center "Security first"]
              [:p.text-center "CircleCI runs in your own VPC, where you have control over security and network settings.  Now you don't have to choose between using state-of-the-art CI and CD tools and complying with your company security policies."]]]]]]
         [:div.outer-section
          [:section.container
           (common/feature-icon "phone")
           [:h2.text-center "Learn More About CircleCI Enterprise"]
           [:div.enterprise-cta-contact
            (om/build contact-form nil)]]]]))

(defrender enterprise-aws [app owner]
  (html [:div#enterprise
         [:div.jumbotron
          enterprise-bg
          [:section.container
           [:div.row
            [:article.hero-title.center-block
             [:div.text-center]
             [:h1.text-center "Next-Generation CI and CD on"
              [:img.inline-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-aws.svg")
                                 :style {:margin-left "20px"}}]]
             [:h3.text-center "Everything you love about CircleCI combined with everything you love about AWS, all on your own Virtual Private Cloud."]]]

           [:div.row.text-center
            [:a.btn.btn-cta {:href "#contact-form"} "Get More Info"]]]]
         [:div.outer-section
          [:div.container
           [:section.row
            [:div.col-xs-6
             [:article
              (common/feature-icon "github")
              [:h2.text-center "Integrates with GitHub Enterprise"]
              [:p.text-center "CircleCI has worked seamlessly with GitHub from the very beginning, and the same is true for CircleCI Enterprise. Enjoy the same PR status intgegration and navigation from CircleCI builds to GitHub commits that is available on circleci.com."]]]
            [:div.col-xs-6
             [:article
              (common/feature-icon "aws")
              [:h2.text-center "CircleCI knows AWS"]
              [:p.text-center
               "The public CircleCI.com offering itself runs on AWS, so you can take advantage of our extensive experience with AWS machines. We've run literally millions of builds using CircleCI AWS hardware, and we can help you choose the best hardware for any workload."]]]]
           [:section.row.extra-row
            [:div.col-xs-6
             [:article
              (common/feature-icon "circle")
              [:h2.text-center "Don't go anywhere"]
              [:p.text-center "You can run your own CircleCI installation right on your existing AWS account. Whether you manage your infrastructure with AWS CloudFormation, Chef, Puppet, Terraform, or none of the above, we'll meet you where you are and integrate seemlessly with your setup."]]]
            [:div.col-xs-6
             [:article
              (common/feature-icon "security")
              [:h2.text-center "Built for Security"]
              [:p.text-center "CircleCI runs in your own Virtual Private Cloud, where everything from firewall settings to IAM privileges are under your control. CircleCI only requires a few simple resource types and network settings, so you can get started quickly and securely."]]]]]]
         [:div.outer-section
          [:section.container
           (common/feature-icon "phone")
           [:h2.text-center "Learn More About CircleCI Enterprise"]
           [:div.enterprise-cta-contact
            (om/build contact-form nil)]]]]))
