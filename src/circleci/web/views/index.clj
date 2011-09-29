(ns circleci.web.views.index
  (use noir.core
       hiccup.core
       hiccup.page-helpers)
  (use circleci.web.views.common)
  (:require [circleci.model.beta-notify :as beta]))

(defpage [:post "/"] {:as request}
  (with-conn
               (beta/insert {:email (:email request)
                             :environment ""
                             :features ""})
               (redirect "/beta-thanks")))

(defpage "/" []
  (layout
   [:div#pitch_wrap
    [:div#ci_beta_signup
    [:div#pitch

     [:div#cileft
      [:h1#cititle "Continuous Integration" [:br] "made easy"]]

     [:div#ciright
      [:div

      [:h3#takepart "Take part in the beta"]
      [:p
       [:span "We'll email you when we're ready."]]
      [:form {:action "/" :method "POST"}
       [:fieldset
        (unordered-list
         [(list (text-field {:id "email"
                             :type "text"} "email" "Email address"))
          (list (check-box {:id "contact"
                             :name "contact"
                             :checked true} "contact")
                (label "contact" "May we contact you to ask about your platform?"))])]
       [:fieldset
        [:input.call_to_action {:type "submit"
                                :value "Get Notified"}]]]]]
     [:div.clear]]]]

   [:div#content_wrap
    [:div#content
     [:div#main_content_wide.left
      [:div.box_medium.feature.separator_r
       [:img {:src "img/icon_feature_03.png"
              :width 60
              :height 55}]
       [:h3 "No more build breaks"]
       [:p "Circle runs automated tests, builds artifacts, manages integration branches and deploys to production, with ease" ]]
      [:div.box_medium.feature
       [:img {:src "img/icon_feature_02.png"
              :width 60
              :height 55}]
       [:h3 "Staged Deployments"]
       [:p "Control when and how to deploy to automatically production, after the tests pass"]]
      [:div.box_medium.feature.separator_r
       [:img {:src "img/icon_feature_01.png"
              :width 60
              :height 55}]
       [:h3 "Easy Github Integration"]
       [:p "Automatically run the tests after every commit"]]
      [:div.box_medium.feature
       [:img {:src "img/icon_feature_04.png"
              :width 60
              :height 55}]
       [:h3 "Parallel testing"]
       [:p "Reduce test time by running the tests in parallel on multiple boxes"]]]]]))
