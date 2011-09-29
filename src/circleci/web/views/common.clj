(ns circleci.web.views.common
  (use noir.core
       hiccup.core
       hiccup.page-helpers))

;; This is hard coded for the signup-page-as-frontpage. Go back some revisions to find the original.

(defn css [path & {:keys [rel type media title] :as opts}]
  [:link (merge {:href path} opts)])

(defn google-analytics []
  (html
   [:script {:type "text/javascript"} "
  var _gaq = _gaq || [];
  _gaq.push(['_setAccount', 'UA-25580673-1']);
  _gaq.push(['_trackPageview']);

  (function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
  })();"]))

(defpartial layout [& content]
            (html5
             [:head
              (google-analytics)
              [:meta {:name "google-site-verification" :content "rCckS33lTuN6tiIrxLMykv_uRR0dMoHNM4XrR4yYUQ0"}]
              [:meta {:http-equiv "Content-Type"
                      :content "text/html; charset=utf-8"}]
              [:meta {:name "description"
                      :content "Continuous Integration made easy"}]
              [:meta {:name "keywords"
                      :content "Circle, heroku, continuous integration, continuous deployment, CI, github"}]
              (css "css/reset.css" :rel "stylesheet" :type "text/css" :media "screen")
              (css "css/core.css" :rel "stylesheet" :type "text/css" :media "screen")
              (css "css/colors_blue_and_green.css" :rel "stylesheet" :type "text/css" :title "Blue and green" :media "screen")
              (css "css/additional.css" :rel "stylesheet" :type "text/css" :media "screen")
              (css "css/wufoo.css" :rel "stylesheet" :type "text/css" :media "screen")
              ;; (css "css/colors_black_and_orange.css" :rel "alternate stylesheet" :type "text/css" :title "Black and orange" :media "screen")
              ;; (css "css/colors_green_and_brown.css" :rel "alternate stylesheet" :type "text/css" :title "Green and brown" :media "screen")
              ;; (css "css/colors_yellow_and_black.css" :rel "alternate stylesheet" :type "text/css" :title "Yellow and black" :media "screen")
              ;; (css "css/colors_teal_and_brown.css" :rel "alternate stylesheet" :type "text/css" :title "Teal and brown" :media "screen")
              ;; (css "css/colors_cherry_and_yellow.css" :rel "alternate stylesheet" :type "text/css" :title "Cherry and yellow" :media "screen")
              "<!--[if lte IE 8]>
		<link href=\"css/ie.css\" rel=\"stylesheet\" type=\"text/css\" media=\"screen\" />
              <![endif]-->"
              "<!--[if lte IE 10]>
              <script src=\"http://html5shiv.googlecode.com/svn/trunk/html5.js\"></script>
              <![endif]-->"
              (css "http://fonts.googleapis.com/css?family=PT+Sans" :rel "stylesheet" :type "text/css" :media "screen")
              (include-js "js/jquery_minimized_core.js" "js/wufoo.js")
              [:script
               "$(function(){
		    $('#slider').slides({
		        preload: true,
			play: 5000,
			pause: 2500,
			hoverPause: true});});"]
              [:title "Circle - Continuous Integration made easy"]]
             [:body.noI.ltr
              [:div#header_wrap
               [:div#header
                [:h1#logo (link-to {:title "Go to Circle homepage"} "/" "Circle")]
                (unordered-list {:id "nav"}
                                [(link-to {:class "current_page"}
                                          "/" "Signup")
                                 ])
                [:div.clear]]]
              content
              [:div.clear]
              [:div#footer_wrap
               [:div#footer
                [:div.box_wide.separator_r
                 [:p#copyright "Copyright &copy; 2011 Circle"]]
                [:div#social_info.box_small.separator_r
                 [:ul
                  [:li#twitter [:a {:href "#"} "follow us"] " on twitter"]
                  [:li#facebook [:a {:href "#"} "follow us"] " on facebook"]]]
                [:div#contact_info.box_small
                 [:h5 "Contact"
                  (unordered-list
                   ["questions@circleci.com"])]]
                [:div.clear]]]]))
