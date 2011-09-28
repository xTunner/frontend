(ns circleci.web.views.signup
  (:use noir.core
       hiccup.core
       hiccup.page-helpers
       hiccup.form-helpers)
  (:use circleci.web.views.common)
  (:use [circleci.db :only (with-conn)])
  (:use [ring.util.response :only (redirect)])
  (:require [circleci.model.beta-notify :as beta]))

(defpage "/signup" []
  (layout
   [:div#page_title_wrap
    [:div#page_title
     [:h3 "Signup for CircleCI"]
     [:div#clear]]]
   [:div#content_wrap
    [:div#content
     [:div#main_content_wide_wpadding.left
      [:h1 "Sign up"]
      [:p.subheading "CircleCI is in alpha right now. Provide your contact information, and we'll contact you when it's ready to try out."]
      [:p.subheading "Tell us about your environment (which heroku stack, which languages, tools you're using, etc), and which features are most important to you, and we'll prioritize them."]
      [:form#account {:action "/signup" :method "POST"}
       [:fieldset
        [:h3 "Information"]
        (unordered-list
         [(list (label "email" "Email address")
                (text-field {:id "email"
                             :type "text"} "email"))
          (list (label "environment" "Your environment")
                (text-area {:id "environment"
                            :type "text"
                            :rows "3"
                            :cols "50"} "environment"))
          (list (label "priorities" "Important features")
                (text-area {:id "priorities"
                            :type "text"
                            :rows "3"
                            :cols "50"} "comments"))])]
       [:fieldset
        [:input.call_to_action {:type "submit"
                                :value "Get Notified"}]]]]]]
   [:div.clear]))

(defpage "/beta-thanks" []
  (layout
   [:div#page_title_wrap
    [:div#page_title
     [:h3 "Success"]]]
   [:div#content_wrap
    [:div#content
     [:div "Thanks for your input! We'll contact you as soon as we're ready."]]]))
