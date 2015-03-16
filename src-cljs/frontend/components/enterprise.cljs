(ns frontend.components.enterprise
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
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
  [:img.language {:class name
                  :src (utils/cdn-path (str "/img/outer/languages/language-" name ".svg"))}])

(defn contact-form
  [app owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:company nil
       :email nil
       :phone nil
       :developer-count nil
       :name nil
       :notice nil})
    om/IRenderState
    (render-state [_ {:keys [email name phone company developer-count notice loading?] :as st}]
      (let [clear-notice! #(om/set-state! owner [:notice] nil)]
        (html
          [:form.form-horizontal {:data-purpose "contact-form"}
           [:div.row.contact-form
            [:div.col-sm-4.col-sm-offset-2
             [:input.input-lg {:value company
                               :type "text"
                               :name "company"
                               :required true
                               :class (when loading? "disabled")
                               :on-change #(do
                                             (clear-notice!)
                                             (om/set-state! owner [:company] (.. % -target -value))
                                             true)
                               :placeholder "Company"}]
             [:input.input-lg {:value name
                               :type "text"
                               :name "name"
                               :required true
                               :class (when loading? "disabled")
                               :on-change #(do
                                             (clear-notice!)
                                             (om/set-state! owner [:name] (.. % -target -value)))
                               :placeholder "Name"}]
             [:input.input-lg {:value email
                               :type "email"
                               :name "email"
                               :require true
                               :class (when loading? "disabled")
                               :on-change #(do
                                             (clear-notice!)
                                             (om/set-state! owner [:email] (.. % -target -value)))
                               :placeholder "Email"}]]
            [:div.col-sm-4
             [:input.input-lg {:value phone
                               :type "text"
                               :name "phone"
                               :on-change #(do
                                             (clear-notice!)
                                             (om/set-state! owner [:phone] (.. % -target -value)))
                               :placeholder "Phone"}]
             [:input.input-lg {:value developer-count
                               :type "text"
                               :name "developer-count"
                               :on-change #(do
                                             (clear-notice!)
                                             (om/set-state! owner [:developer-count] (.. % -target -value)))
                               :placeholder "# of Developers"}]
             [:div.telephone-info
              "Or call "
              [:a.telephone-number {:href "tel:+14158515247"} "415.851.5247"]
              " for an Enterprise quote."]]]
           [:div.row
            [:div.col-xs-12.text-center
             [:button.btn.btn-cta
              {:on-click #(do (cond
                                (not (utils/valid-email? email))
                                (om/set-state! owner [:notice] {:type "error"
                                                                :message "Please enter a valid email address."})

                                :else
                                (do
                                  (om/set-state! owner [:loading?] true)
                                  (go (let [resp (<! (ajax/managed-form-post
                                                       "/about/contact"
                                                       :params (merge {:name name
                                                                       :email email
                                                                       :message (gstr/format "Company: %s\nPhone: %s\nDeveloper count: %s" company phone developer-count)
                                                                       :enterprise true})))]
                                        (if (= (:status resp) :success)
                                          (om/update-state! owner (fn [s]
                                                                    {:name ""
                                                                     :email ""
                                                                     :phone ""
                                                                     :company ""
                                                                     :developer-count ""
                                                                     :loading? false
                                                                     :notice (:resp resp)}))
                                          (do
                                            (om/set-state! owner [:loading?] false)
                                            (om/set-state! owner [:notice] {:type "error" :message "Sorry! There was an error sending your message."})))))))
                              false)}
              (if loading? "Sending..." "Get More Info")]]]])))))

(defn enterprise [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div#enterprise
        [:div.jumbotron
         (map arrow ["left-a-1"
                     "left-a-2"
                     "left-a-3"
                     "left-a-4"
                     "right-a-1"
                     "right-a-2"
                     "right-a-3"
                     "right-a-4"])
         ;; [:img.arrow {:src (utils/cdn-path "/img/outer/enterprise/arrow-left-a-1.svg")}]
         [:section.container
          [:div.row
           [:article.hero-title.center-block
            [:div.text-center
             [:img.hero-logo {:src (utils/cdn-path "/img/outer/enterprise/logo-circleci.svg")}]]
            [:h1.text-center "Ship code at the speed of business."]
            [:h3.text-center "The same Continuous Integration and Deployment platform that developers love, with added security for the enterprise. CircleCI Enterprise lets you quickly and securely build, test, and deploy your applications."]]]
           
          [:div.row.text-center
           [:button.btn.btn-cta
            {:on-click #(utils/scroll-to-selector! "form[data-purpose='contact-form']")}
            "Get More Info"]]]]
        ;; need this wrapper for border-top to span the full screen
        [:div.outer-section
         [:div.container
          [:section.row
           [:div.col-xs-4
            [:article
             (common/feature-icon "deploy-1")
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
        [:section.enterprise-story
          (map language ["rails-2"
                         "clojure-2"
                         "java-2"
                         "php-2"])
         [:div.container
          [:div.row
           [:div.col-xs-8.col-xs-offset-2
            [:img.story-logo {:src (utils/cdn-path "/img/outer/customers/customer-shopify.svg")}]
            [:blockquote
             "CircleCI lets us be more agile and ship product faster. We can focus on delivering value to our customers, not maintaining CI Infrastructure."
             [:footer
              [:strong "John Duff"]
              ", Director of Engineering at "
              [:strong "Shopify"]
              [:br]
              [:cite
               [:a {:href "/stories/shopify"} "Read the Story"]]]]]]]]

        [:div.outer-section
         [:section.container
          (common/feature-icon "phone")
          [:h2.text-center "Learn More About CircleCI Enterprise"]
          [:div.enterprise-cta-contact
           (om/build contact-form app)]]]]))))
