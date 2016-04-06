(ns frontend.components.enterprise
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.contact-form :as contact-form]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [goog.string :as gstr]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html inspect]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

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
                      :message (->> [["Company" company]
                                     ["Phone" phone]
                                     ["Developer count" developer-count]
                                     []
                                     ["Form URL" js/location.href]
                                     []]
                                    (map (fn [[k v]] (if k (gstr/format "%s: %s" k (or v "not set")) "")))
                                    (str/join "\n"))
                      :enterprise true})}
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
           [:a.telephone-number {:href "tel:+14155800944"} "415.580.0944"]
           " for an Enterprise quote."]]]]]
      (om/build contact-form/transitionable-height
                {:class "notice"
                 :children (html
                            (when notice
                              [:div {:class (:type notice)}
                               (:message notice)]))})
      [:div.row
       [:div.col-xs-12.text-center
        (om/build contact-form/morphing-button {:text "Get In Touch..." :form-state form-state})
        [:div.success-message
         {:class (when (= :success form-state) "success")}
         "Thank you for submitting your information."
         [:br]
         "Someone from our Enterprise team will contact you soon, usually within one business day."]]]))))

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
            [:h1.text-center "CircleCI power behind your firewall"]
            [:h3.text-center "CircleCI Enterprise delivers the same Continuous Integration and Deployment platform that developers love with the added security and configurability that comes from running inside your private cloud or data center."]]]
          [:div.row.text-center
           [:a.btn.btn-cta {:href "#contact-form"} "Get More Info"]]]]
        ;; need this wrapper for border-top to span the full screen
        [:div.outer-section
         [:div.container
          [:section.row
           [:div.col-xs-4
            [:article
             (common/feature-icon "circle")
             [:h2.text-center "Fewer Headaches"]
             [:p "Reduce DevOps management overhead and ship with confidence. CircleCI Enterprise has all the modern, powerful, and easily managed "
              [:a {:href "/features"} "CircleCi.com features"]
              " that are trusted by tens of thousands of developers around the world, so your teams can quickly and securely build, test, and deploy with confidence."]]
            ]
           [:div.col-xs-4
            [:article
             (common/feature-icon "security")
             [:h2.text-center "World-Class Security"]
             [:p
              "Secure your software assets for peace of mind. CircleCI Enterprise runs in your private cloud or data center and uses modern security practice, so your internal and regulatory security requirements can be met."]]]
           [:div.col-xs-4
            [:article
             (common/feature-icon "controls")
             [:h2.text-center "Total Control"]
             [:p
              "Ship faster and easier. With CircleCI Enterprise you can customize your build environment and optimize the configuration and scale of the computing power of your build fleet specifically to the needs of your software teams."]]]]]]
         [:div.outer-section
          [:div.container
           [:section.row
            [:div.col-xs-8.col-xs-offset-2.enterprise-section
             [:h2.text-center "Supported Installation Environments"]
              [:p "CircleCI Enterprise is currently available for deployment on "
               [:a {:href "/enterprise/aws"} "AWS"]
               " and "
               [:a {:href "/enterprise/azure"} "Azure"]
               ". Support for others such as VMware and OpenStack will be available soon. "
               [:a {:href "#contact-form"} "Get in touch"]
               " to let us know what deployment environment you would like to see available next."]]]
           [:section.row
            [:div.col-xs-8.col-xs-offset-2.enterprise-section
             [:h2.text-center "Integrations"]
             [:p "CircleCI is built for unlimited flexibility. From hosting options to test frameworks or programming frameworks, we let you use the technology you need. Easily integrate directly with GitHub.com and GitHub Enterprise, Docker, SauceLabs, Heroku, and many more."]]]]]
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
          [:div.col-xs-8.col-xs-offset-2
           [:p "CircleCI Enterprise is available for a free, no obligation, 30 day trial.   Simply fill out the form below to reach out, tell us a little about your team, and we will follow-up to provide you with access and a trial key."]]
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
              [:p.text-center "Run your own CircleCI installation together with your existing Azure infrastructure. You've invested time, money, and people in your cloud infrastructure. CircleCI Enterprise on Azure makes those investments pay dividends by integrating seamlessly with your existing setup."]]]
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
              [:p.text-center "You can run your own CircleCI installation right on your existing AWS account. Whether you manage your infrastructure with AWS CloudFormation, Chef, Puppet, Terraform, or none of the above, we'll meet you where you are and integrate seamlessly with your setup."]]]
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
