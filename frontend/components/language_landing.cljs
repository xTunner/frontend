(ns frontend.components.language-landing
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn arrow-class [selected-testimonial]
  (case selected-testimonial
    0 "arrowLeft"
    1 "arrowCenter"
    2 "arrowRight"
    "arrowLeft"
    ))

(def templates {"ruby" {:language "Ruby"
                        :headline "CircleCI makes Continous Integration and Deployment for Ruby projects a breeze."
                        :logo-path (utils/cdn-path "/img/outer/languages/ruby-logo.svg")
                        :ss-1 "/assets/img/outer/languages/ruby-ss-1.png"
                        :ss-2 "/assets/img/outer/languages/ruby-ss-1.png"
                        :ss-3 "/assets/img/outer/languages/ruby-ss-1.png"
                        :features [{:feature "CircleCI uses RVM to provide support for a wide variety of Ruby versions and gems. It is also trivial to add any packages or frameworks that are not installed on our machines by default, allowing you to effortlessly customize your test enviroment.  CircleCI also supports Test::Unit, RSpec, Cucumber, Spinach, Jasmine, Konacha, and just about any other testing framework you use for your Ruby project."
                                    :title "Built For Ruby"
                                    :icon (utils/cdn-path "/img/outer/languages/gear-icon.svg")}
                                   {:feature "Circle manages all your database requirements for your, such as running your rake commands for creating, loading, and migrating your database. We have pre-installed more than a dozen databases and queues, including PostgreSQL, MySQL, and MongoDB. You can also add custom database commands via your circle.yml."
                                    :title "Database Management"
                                    :icon (utils/cdn-path "/img/outer/languages/file-icon.svg")}
                                   {:feature "For the majority of Ruby projects no configuration is required; you just run your builds on CircleCI and it works! CircleCI will automatically infer your test commands if you're using Test::Unit, RSpec, Cucumber, Spinach, Jasmine, or Konacha."
                                    :title "Inference That Just Works"
                                    :icon (utils/cdn-path "/img/outer/languages/book-icon.svg")}]
                        :docs-link "/docs/language-ruby-on-rails"
                        :testimonials [{:text "Nullam id dolor id nibh ultricies vehicula ut id elit. Duis mollis, est non commodo luctus, nisi erat porttitor ligula, eget lacinia odio sem nec elit. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Integer posuere erat a ante venenatis."
                                        :img "/assets/img/outer/stories/john.jpg"
                                        :author "Kevin Rose"
                                        :title "Entrepneur @Google"}
                                       {:text "Nullam id dolor id nibh ultricies vehicula ut id elit. Duis mollis, est non commodo luctus, nisi erat porttitor ligula, eget lacinia odio sem nec elit. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Integer posuere erat a ante venenatis."
                                        :img "/assets/img/outer/stories/john.jpg"
                                        :author "Clark Kent"
                                        :title "superman"}
                                       {:text "Nullam id dolor id nibh ultricies vehicula ut id elit. Duis mollis, est non commodo luctus, nisi erat porttitor ligula, eget lacinia odio sem nec elit. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Integer posuere erat a ante venenatis."
                                        :img "/assets/img/outer/stories/john.jpg"
                                        :author "Bruce Wayne"
                                        :title "Batman"}]}})

(defn language-landing [app owner]
  (reify
    om/IRender
    (render [_]
            (let [subpage (get-in app [:navigation-data :language])
                  template (get templates subpage)
                  selected-testimonial (get-in app state/language-testimonial-tab-path 0)
                  controls-ch (om/get-shared owner [:comms :controls])]
              (html
                [:div.languages.page
                 [:div.languages-head
                  [:img {:src (:logo-path template)}]
                  [:h1 (:headline template)]
                  [:div.languages-screenshots]
                  [:div.hero-shadow]]
                 [:div.languages-body
                  [:div.languages-features
                   [:div.center-text
                    [:h3 "FEATURES"
                     ]
                    ]
                   (map-indexed
                     (fn [i feature] [feature (:features template)]
                       (if (odd? i)
                         [:div.feature
                          [:div.feature-image
                           [:img {:src (:icon feature)}]
                           ]
                          [:div.feature-copy
                           [:h4.feature-title (:title feature)
                            ]
                           [:p.feature-description (:feature feature)]
                           ]

                          ]
                         [:div.feature
                          [:div.feature-copy
                           [:h4.feature-title (:title feature)
                            ]
                           [:p.feature-description (:feature feature)]
                           ]
                          [:div.feature-image
                           [:img {:src (:icon feature)}]
                           ]
                          ]))
                     (:features template))

                   [:div.button
                    [:a {:href (:docs-link template)} "Read documentation on " (:language template)]]
                   ]
                  ]
                 [:div.languages-testimonials {:class (arrow-class selected-testimonial)}
                  [:div.languages-body
                   [:div.center-text
                    [:h3 "TESTIMONIALS"
                     ]
                    ]
                   [:div.testimonial-authors
                    (map-indexed (fn [i testimonial] [:img {:src (:img testimonial) :on-click #(put! controls-ch [:language-testimonial-tab-selected {:index i}])}])
                                 (:testimonials template))]
                   [:div.testimonial-box
                    [:div.testimonial
                     [:p.testimonial-text (get-in template [:testimonials selected-testimonial :text])]

                     [:div.testimonial-author "—" (get-in template [:testimonials selected-testimonial :author])]
                     [:div.testimonial-author-title (get-in template [:testimonials selected-testimonial :title])]]
                    ]
                   ]
                  ]

                 [:div.languages-cta
                  [:div.languages-body
                   [:h3
                    "How do I start using my " (:language template) " app with CircleCI?"]
                   [:div.languages-cta-steps
                    [:div.languages-cta-step
                     [:div.step-number "1"]
                     [:div
                      "Start by signing up "
                      [:br]
                      "using GitHub"]
                     ]
                    [:div.languages-cta-step
                     [:div.step-number "2"]
                     [:div
                      "Run one of your Ruby projects on Circle"]]
                    [:div.languages-cta-step
                     [:div.step-number "3"]
                     [:div
                      [:strong "That's it!"]
                      " Contact support if you run in to any issues. "]
                     ]]
                   [:div.cta-divider]
                   [:div.center-text
                    [:a.languages-cta-button
                     {:href (auth-url)
                      :on-click #(put! controls-ch [:track-external-link-clicked {:event "Auth GitHub"
                                                                                  :properties {:source (:language template)}
                                                                                  :path (auth-url)}])}
                     "Sign Up With GitHub"]
                    [:div.language-cta-trial "14-day free trial"]
                    ]
                   ]]

                 ])))))
