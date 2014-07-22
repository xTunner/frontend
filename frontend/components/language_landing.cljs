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

(defn arrow-class [selected-testimonial]
  (case selected-testimonial 
    0 "arrowLeft"
    1 "arrowCenter"
    2 "arrowRight"
    "arrowLeft"
    ))

(def templates {"ruby" {:language "Ruby"
                        :headline "CircleCI makes Continous Integration and Deployment for Ruby projects a breeze."
                        :logo-path "/assets/img/outer/languages/ruby-logo.png"
                        :ss-1 "/assets/img/outer/languages/ruby-ss-1.png"
                        :ss-2 "/assets/img/outer/languages/ruby-ss-1.png"
                        :ss-3 "/assets/img/outer/languages/ruby-ss-1.png"
                        :testimonial-author-1 "/assets/img/outer/stories/john.jpg"
                        :testimonials [{:text "Nullam id dolor id nibh ultricies vehicula ut id elit. Duis mollis, est non commodo luctus, nisi erat porttitor ligula, eget lacinia odio sem nec elit. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Integer posuere erat a ante venenatis."
                                       :img "/assets/img/outer/stories/john.jpg"
                                       :author "Kevin Rose"
                                       :title "Entrepneur @Google"}
                                       {:text "text 2"
                                        :img "/assets/img/outer/stories/john.jpg"
                                        :author "Clark Kent"
                                        :title "superman"}
                                       {:text "text 3"
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
                  [:div.languages-screenshots
                   [:img {:src (:ss-1 template)}]
                   [:img {:src (:ss-2 template)}]
                   [:img {:src (:ss-3 template)}]]]
                 [:div.languages-body.remove-margin
                  [:div.languages-features
                   [:div.center-text
                    [:h3 "FEATURES"
                     ]
                    ]
                   [:div.feature
                    [:div.feature-image
                     [:img {:src "/assets/img/outer/languages/gear-icon.png"}]
                     ]
                    [:div.feature-copy
                     [:h4.feature-title "Testing Frameworks"
                      ]
                     [:p.feature-description "Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Maecenas faucibus mollis interdum. Morbi leo risus, porta ac consectetur ac, vestibulum at eros. Aenean eu leo quam. Pellentesque ornare sem lacinia quam venenatis vestibulum. Aenean lacinia bibendum nulla sed consectetur."] 
                     ]
                    
                    ]
                   [:div.feature
                    [:div.feature-copy
                     [:h4.feature-title "Testing Frameworks"
                      ]
                     [:p.feature-description "Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Maecenas faucibus mollis interdum. Morbi leo risus, porta ac consectetur ac, vestibulum at eros. Aenean eu leo quam. Pellentesque ornare sem lacinia quam venenatis vestibulum. Aenean lacinia bibendum nulla sed consectetur."] 
                     ]
                    [:div.feature-image
                     [:img {:src "/assets/img/outer/languages/book-icon.png"}]
                     ]
                    ]
                   [:div.feature
                    [:div.feature-image
                     [:img {:src "/assets/img/outer/languages/file-icon.png"}]
                     ]
                    [:div.feature-copy
                     [:h4.feature-title "Testing Frameworks"
                      ]
                     [:p.feature-description "Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor. Maecenas faucibus mollis interdum. Morbi leo risus, porta ac consectetur ac, vestibulum at eros. Aenean eu leo quam. Pellentesque ornare sem lacinia quam venenatis vestibulum. Aenean lacinia bibendum nulla sed consectetur."] 
                     ]
                    
                    ]
                   [:div.button 
                    [:a {:href="#"} "Read documentation on " (:language template)]]
                   ]
                  ]
                 [:div.languages-testimonials
                  [:div.languages-body.remove-margin
                   [:div.center-text
                    [:h3 "TESTIMONIALS"
                     ]
                    ]
                   [:div.testimonial-authors
                    (map-indexed (fn [i testimonial] [:img {:src (:img testimonial) :on-click #(put! controls-ch [:language-testimonial-tab-selected {:index i}])}])
                                 (:testimonials template))]
                   [:div.testimonial-box {:class (arrow-class selected-testimonial)}
                    [:div.testimonial
                     [:p.testimonial-text (get-in template [:testimonials selected-testimonial :text])]
                     
                     [:div.testimonial-author "- " (get-in template [:testimonials selected-testimonial :author])]
                     [:div.testimonial-author-title (get-in template [:testimonials selected-testimonial :title])]]
                    ;;[:div.testimonial
                    ;;[:div.testimonial-text (:testimonial-2 template)]]
                    ;;[:div.testimonial
                    ;;[:div.testimonial-text (:testimonial-3 template)]]
                    ]
                   ]
                  ]
                 [:div.languages-body
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
                   ]]
                 
                 ])))))
