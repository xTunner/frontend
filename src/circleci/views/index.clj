(ns circleci.views.index
  (use noir.core
       hiccup.core
       hiccup.page-helpers)
  (use circleci.views.common))

(defpage "/" []
  (layout
   [:div#pitch_wrap
    [:div#pitch
     [:div#intro
      [:h1 "Continuous Integration for Heroku"]
      [:p "CircleCI runs automated tests, manages integration branches and deploys to production, all with ease"]
      [:a.call_to_action {:href "/signup"} "Get started now"]]
     [:div#slider
      [:div.slider_images
       (for [img ["img/screenshot_01.jpg"
                  "img/screenshot_02.jpg"
                  "img/screenshot_03.jpg"
                  "img/screenshot_04.jpg"]]
         (image {:width 420
                 :height 280} img))]]
     [:div.ie7_sliderFix]
     [:div.clear]]]
   [:div#addendum_wrap
    [:div#addendum
     [:div#callout.box_wide.separator_r
      [:a {:href "img/screeshot_large_01.jpg"
           :rel "lightbox"}]
      [:img {:src "img/screenshot_small_01.jpg"
             :width 190
             :height 60}]
      [:h3 "An important feature"]
      [:p "This area will contain either one of the  most important features or one of the top benefits of using your product or choosing your company. This paragraph could be accompanied by one screenshot exempifying that feature."]]
     [:div#testimonials.box_wide
      [:h3 "Here's what our imaginary customers are saying"]
      [:blockquote.separator_r
       "CircleCI is the best thing since Heroku!"
       [:cite "Mike Mikington, Mike Inc." [:img {:src "img/customer_01.jpg"
                                                 :width 30
                                                 :height 30
                                                 :alt "Mike Mikington"}]]]
      [:blockquote "I love the GitHub integration and easy setup"
       [:cite "Serious Samy, Solaris"
        [:img {:src "img/customer_02.jpg"
               :width 30
               :height 30
               :alt "Serious Sam"}]]]]
     [:div.clear]]]
   [:div#content_wrap
    [:div#content
     [:div#main_content_wide.left
      [:div.box_medium.feature.separator_r
       [:img {:src "img/icon_feature_01.png"
              :width 60
              :height 55}]
       [:h3 "Easy Github Integration"]
       [:p "Automatically run the tests after every commit"]]
      [:div.box_medium.feature
       [:img {:src "img/icon_feature_02.png"
              :width 60
              :height 55}]
       [:h3 "Staged Deployments"]
       [:p "Control when and how to deploy to automatically production, after the tests pass"]]
      [:div.box_medium.feature.separator_r
       [:img {:src "img/icon_feature_03.png"
              :width 60
              :height 55}]
       [:h3 "No more build breaks"]
       [:p "Each developer commits to their own integration branch, CircleCI automatically merges their changes " [:b "only"] " when the tests pass"]]
      [:div.box_medium.feature
       [:img {:src "img/icon_feature_04.png"
              :width 60
              :height 55}]
       [:h3 "Parallel testing"]
       [:p "Reduce test time by running the tests in parallel on multiple boxes"]]]]]))