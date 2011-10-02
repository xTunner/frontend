(ns circleci.web.views.index
  (:use noir.core
        hiccup.core
        hiccup.page-helpers
        hiccup.form-helpers
        circleci.web.views.common
        ring.util.response)
  (:use [circleci.db :only (with-conn)])
  (:use [ring.util.response :only (redirect)])
  (:require [circleci.model.beta-notify :as beta])
  (:require [noir.cookies :as cookies]))


(defpage [:post "/"] {:as request}
  (with-conn
    (beta/insert {:email (:email request)
                 :contact (= "true" (:contact request))
                 :environment ""
                 :features ""})
    (cookies/put! :signed-up "1")
    (redirect "/")))


(declare signupform youre-done)

(defn center-vertically 
  "Take the provided div and center it vertically, by adding classes and
  wrapping it's contents in more divs. It relies on additional.css having
  .vcenter{1,2,3} defined."
  [[tag & args]]
    (let [[m & remaining] args
          property-map (into {:class "vcenter1"} (if (map? m) m {}))
          inner-tags (if (map? m) remaining args)]
         [tag property-map [:div.vcenter2 (apply vector :div.vcenter3 inner-tags)]]))


(defpage "/" []
  (layout
    [:div#pitch_wrap
      [:div#pitch

        (center-vertically 
          [:div#left-panel [:h1#cititle "Continuous Integration" [:br] "made easy"]])
        (center-vertically
          [:div#right-panel (if (cookies/get :signed-up) (youre-done) (signupform))])
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
            [:p "Find out as soon as something breaks, using automatic paralleization, "
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



(defn delete-cookie [name]
  (cookies/put! name {:value ""
                      :max-age 0
                      :expires 0 ; You need expires for IE6-8
                      :path "/"})
  "") ; return a string

(defpartial youre-done [& content]
;  (delete-cookie :signed-up) ; For debugging
  [:div.move-right
    [:h2.blue-box "Thanks!" [:br] "We'll be in touch soon!"]])


