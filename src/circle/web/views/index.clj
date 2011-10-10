(ns circle.web.views.index
  (:use noir.core
        hiccup.core
        hiccup.page-helpers
        hiccup.form-helpers
        circle.web.views.common
        ring.util.response)
  (:use [circle.db :only (with-conn)])
  (:use [ring.util.response :only (redirect)])
  (:require [circle.model.beta-notify :as beta])
  (:require [noir.cookies :as cookies])
  (:require [noir.session :as session]))


(defpage [:post "/"] {:as request}
  (with-conn
    (let [session-key (cookies/get "ring-session")]
      (beta/insert {:email (:email request)
                   :contact (= "true" (:contact request))
                   :environment ""
                   :features ""
                   :session_key session-key})
      (session/put! :signed-up "1")
      (redirect "/"))))


(declare signupform youre-done)

(defpage "/" []
  (layout
    [:div#pitch_wrap
      [:div#pitch

        (center-vertically 
          [:div#left-panel [:h1#cititle "Continuous Integration" [:br] "made easy"]])
        (center-vertically
          [:div#right-panel (if (session/get :signed-up) (youre-done) (signupform))])
        [:div.clear]]]

    [:div#content_wrap
      [:div#content
        [:div#main_content_wide.left

          [:div.box_medium.feature.separator_r
            [:img {:src "img/icon_feature_04.png" :width 60 :height 55}]
            [:h3 "Minimal configuration"]
            [:p "In most cases, we'll just need access to your repo."]]

          [:div.box_medium.feature
            [:img {:src "img/icon_feature_01.png" :width 60 :height 55}]
            [:h3 "Go home early"]
            [:p "Push to a branch, and we'll automatically merge once the tests pass."]]

          [:div.box_medium.feature.separator_r
            [:img {:src "img/icon_feature_02.png" :width 60 :height 55}]
            [:h3 "Lightning fast tests"]
            [:p "Find out as soon as something breaks, using automatic parallelization, "
                "dependency analysis and test prioritization."]]

          [:div.box_medium.feature
            [:img {:src "img/icon_feature_03.png" :width 60 :height 55}]
            [:h3 "Continuous deployment"]
            [:p "Deploy your web app to prodution automatically once tests pass. "
                "Revert automatically based on your analytics data."]]]]]))

(defpartial signupform [& content]
   [:div.move-right
     [:div.blue-box
       [:h2.takepart "Sign up for the beta"]
         [:p.whenready "We'll email you when we're ready."]
         [:form {:action "/" :method "POST"}
           [:fieldset#actualform
             (unordered-list
               [(text-field {:id "email"
                             :type "text"
                             :onfocus "if (this.value == 'Email address') { this.value=''};"
                             :onblur "if (this.value == '') { this.value = 'Email address'};"}
                             "email" "Email address")
                (check-box {:id "contact"
                            :name "contact"
                            :checked true}
                            "contact")
                [:div
                  [:div
                    (label {:id "contact-label"}
                           "contact"
                           "May we contact you to ask about your platform, stack, test suite, etc?")]]])]
           [:fieldset
             [:input.call_to_action {:type "submit"
                                     :value "Get Notified"}]]]]])


(defpartial youre-done [& content]
  (do 
;    (session/remove! :signed-up) ; For debugging
    [:div.move-right
      [:h2.blue-box "Thanks!" [:br] "We'll be in touch soon!"]]))


