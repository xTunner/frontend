(ns frontend.components.language-landing
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))
(def templates {"ruby" {:language "Ruby"
                        :headline "CircleCI makes Continous Integration and Deployment for Ruby projects a breeze."
                        :logo-path "/assets/img/outer/languages/ruby-logo.png"
                        :ss-1 "/assets/img/outer/languages/ruby-ss-1.png"
                        :ss-2 "/assets/img/outer/languages/ruby-ss-1.png"
                        :ss-3 "/assets/img/outer/languages/ruby-ss-1.png"
                        :testimonial-1 "Ruby and Circle are amazing!"
                        :testimonial-2 "Ruby and Circle are spectacular!"
                        :testimonial-3 "Ruby and Circle are badass!"}})
(defn language-landing [app owner]
  (reify
    om/IRender
    (render [_]
            (let [subpage (get-in app [:navigation-data :language])
                  template (get templates subpage)]
              (html
                [:div.languages.page
                 [:div.languages-head
                  [:img {:src (:logo-path template)}]
                  [:h1 (:headline template)]
                  [:div.languages-screenshots
                   [:img {:src (:ss-1 template)}]
                   [:img {:src (:ss-2 template)}]
                   [:img {:src (:ss-3 template)}]]]
                 [:div.languages-body
                  [:div.languages-features
                   "Features"
                   [:div.feature
                    "Feature 1"
                    [:img {:src "/assets/img/outer/languages/gear-icon.png"}]]
                   [:div.feature
                    "Feature 2"
                    [:img {:src "/assets/img/outer/languages/book-icon.png"}]]
                   [:div.feature
                    "Feature 3"
                    [:img {:src "/assets/img/outer/languages/file-icon.png"}]]
                   [:a {:href="#"}
                    [:div.button "Read documentation on " (:language template)]]]
                  [:div.languages-testimonials
                   [:div.testimonial
                    [:img {:src (:logo-path template)}]
                    [:div.testimonial-text (:testimonial-1 template)]]
                   [:div.testimonial
                    [:img {:src (:logo-path template)}]
                    [:div.testimonial-text (:testimonial-2 template)]]
                   [:div.testimonial
                    [:img {:src (:logo-path template)}]
                    [:div.testimonial-text (:testimonial-3 template)]]
                   ]
                  [:div.languages-cta
                   [:div.languages-cta-headline
                    "How do I start using my " (:language template) " app with Circle?"] 
                   [:div.languages-cta-step
                    "Start by signing up using GitHub"]
                   [:div.languages-cta-step
                    "Run one of your Ruby projects on Circle"]
                   [:div.languages-cta-step
                    "That's it! If you hit any problems just us a message and we'll help you out."]
                  [:a.languages-cta-button {:href="#"}
                    "Sign Up With GitHub"]
                   ]
                  
                  ]])))))
