(ns circleci.web.views.index
  (use noir.core
       hiccup.core
       hiccup.page-helpers)
  (use circleci.web.views.common))

(defpage "/" []
  (layout
   [:div#pitch_wrap
    [:div#pitch
     [:div#intro
      [:h1 "Continuous Integration made easy"]
     ]
     [:div#slider ]
     [:div.ie7_sliderFix]
     [:div.clear]]]

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
