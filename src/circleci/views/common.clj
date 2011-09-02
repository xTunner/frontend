(ns circleci.views.common
  (use noir.core
       hiccup.core
       hiccup.page-helpers))

(defn css [path & {:keys [rel type media title] :as opts}]
  [:link (merge {:href path} opts)])

(defpartial layout [& content]
            (html5
             [:head
              [:meta {:name "google-site-verification" :content "rCckS33lTuN6tiIrxLMykv_uRR0dMoHNM4XrR4yYUQ0"}]
              [:meta {:http-equiv "Content-Type"
                      :content "text/html; charset=utf-8"}]
              [:meta {:name "description"
                      :content "The best Continuous Integration Solution for Heroku"}]
              [:meta {:name "keywords"
                      :content "CircleCI, heroku, continuous integration, CI, github"}]
              (css "css/reset.css" :rel "stylesheet" :type "text/css" :media "screen")
              (css "css/core.css" :rel "stylesheet" :type "text/css" :media "screen")
              (css "css/colors_blue_and_green.css" :rel "stylesheet" :type "text/css" :title "Blue and green" :media "screen")
              ;; (css "css/colors_black_and_orange.css" :rel "alternate stylesheet" :type "text/css" :title "Black and orange" :media "screen")
              ;; (css "css/colors_green_and_brown.css" :rel "alternate stylesheet" :type "text/css" :title "Green and brown" :media "screen")
              ;; (css "css/colors_yellow_and_black.css" :rel "alternate stylesheet" :type "text/css" :title "Yellow and black" :media "screen")
              ;; (css "css/colors_teal_and_brown.css" :rel "alternate stylesheet" :type "text/css" :title "Teal and brown" :media "screen")
              ;; (css "css/colors_cherry_and_yellow.css" :rel "alternate stylesheet" :type "text/css" :title "Cherry and yellow" :media "screen")
              (css "css/slider.css" :rel "stylesheet" :type "text/css" :media "screen")
              (css "css/lightbox.css" :rel "stylesheet" :type "text/css" :media "screen")
              "<!--[if lte IE 8]>
		<link href=\"css/ie.css\" rel=\"stylesheet\" type=\"text/css\" media=\"screen\" />
              <![endif]-->"
              (css "http://fonts.googleapis.com/css?family=PT+Sans" :rel "stylesheet" :type "text/css" :media "screen")
              (include-js "js/styleswitcher.js"
                          "js/lightbox.js"
                          "js/jquery_minimized_core.js"
                          "js/jquery_slider.js")
              [:script
               "$(function(){
		    $('#slider').slides({
		        preload: true,
			play: 5000,
			pause: 2500,
			hoverPause: true});});"]
              [:title "CircleCI - A Continuous Integration service for Heroku"]]
             [:body
              [:div#header_wrap
               [:div#header
                [:h1#logo (link-to {:title "Go to CircleCI homepage"} "/" "CircleCI")]
                (unordered-list {:id "nav"}
                                [(link-to {:class "current_page"} ;; TODO - make current_page reflect reality
                                          "/" "Home")
                                 (link-to "/signup" "Signup page")
                                 (link-to "/contact" "Contact")])
                [:div.clear]]]
              content
              [:div.clear]
              [:div#footer_wrap
               [:div#footer
                [:div.box_wide.separator_r
                 (unordered-list {:id "nav_footer"}
                  [(link-to "/" "Home")
                   (link-to "/signup" "Signup")])
                 [:p#copyright "Copyright &copy; 2011 CircleCI"]]
                [:div#social_info.box_small.separator_r
                 [:ul
                  [:li#twitter [:a {:href "#"} "follow us"] " on twitter"]
                  [:li#facebook [:a {:href "#"} "follow us"] " on facebook"]]]
                [:div#contact_info.box_small
                 [:h5 "Contact"
                  (unordered-list
                   ["arohner@gmail.com"])]]
                [:div.clear]]]]))