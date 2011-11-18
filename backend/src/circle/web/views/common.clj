(ns circle.web.views.common
  (:use noir.core
        hiccup.core
        hiccup.page-helpers)
  (:use [circle.web.user-session :only (logged-in?)])
  (:use [circle.web.util :only (post-link)]))

;; This is hard coded for the signup-page-as-frontpage. Go back some revisions to find the original.

(defn css [path & {:keys [rel type media title] :as opts}]
  [:link (merge {:href (resolve-uri path)} opts)])

(defn center-vertically
  "Take the provided div and center it vertically, by adding classes and
  wrapping it's contents in more divs. It relies on additional.css having
  .vcenter{1,2,3} defined."
  [[tag & args]]
  (let [[m & remaining] args
        property-map (into {:class "vcenter1"} (if (map? m) m {}))
        inner-tags (if (map? m) remaining args)]
    [tag property-map [:div.vcenter2 (apply vector :div.vcenter3 inner-tags)]]))


(defn center-vertically-span
  "Take the provided div and center it vertically, by adding classes and
  wrapping it's contents in more divs. It relies on additional.css having
  .vcenter{1,2,3} defined."
  [[tag & args]]
  (let [[m & remaining] args
        property-map (into {:class "vcenter1"} (if (map? m) m {}))
        inner-tags (if (map? m) remaining args)]
    [tag property-map [:span.vcenter2 (apply vector :span.vcenter3 inner-tags)]]))

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


(defn login-box []
  (if (logged-in?)
    (post-link "/logout" (format "Logout"))
    (link-to "/login" "Login")))

(defpartial layout
  ;; "Options - a map,
  ;;    :absolute-urls - if true, css and other static assets will use absolute URLs rather than relative"
  [options & content]
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
    (css "/css/reset.css" :rel "stylesheet" :type "text/css" :media "screen")
    (css "/css/core.css" :rel "stylesheet" :type "text/css" :media "screen")
    (css "/css/colors_blue_and_green.css" :rel "stylesheet" :type "text/css" :title "Blue and green" :media "screen")
    (css "/css/additional.css" :rel "stylesheet" :type "text/css" :media "screen")
    (css "/css/wufoo.css" :rel "stylesheet" :type "text/css" :media "screen")
    "<!--[if lte IE 8]>
		<link href=\"css/ie.css\" rel=\"stylesheet\" type=\"text/css\" media=\"screen\" />
              <![endif]-->"
    "<!--[if lte IE 10]>
              <script src=\"http://html5shiv.googlecode.com/svn/trunk/html5.js\"></script>
              <![endif]-->"
    (css "http://fonts.googleapis.com/css?family=PT+Sans" :rel "stylesheet" :type "text/css" :media "screen")
    (include-js "/js/jquery_minimized_core.js" "/js/wufoo.js")
    [:title "Circle - Continuous Integration made easy"]]
   [:body
    [:div#notthefooter
     [:div#header_wrap
      [:div#header
       [:h1#logo (link-to {:title "Go to Circle homepage"} "/" [:img#circle {:src "/img/circle-transparent.png"}]
                          [:img#circle-word {:src "/img/circle-word.png"}])]
       (unordered-list {:id "nav"}
                       [(link-to {:class "current_page"}
                                 "/" "Signup")
;                        (login-box)
                        ])
       [:div.clear]]]
     content
     [:div.clear]
     [:div#notthefooterclear]]
    [:div#footer_wrap
     [:div#footer
      [:div.box_wide.separator_r
       [:p#copyright "Copyright &copy; 2011 Circle"]]
      [:div#social_info.box_small.separator_r
       [:ul
        [:li#twitter [:a {:href "http://twitter.com/circleci"} "follow @circleci"]]]]
      [:div#contact_info.box_small
       [:h5
        (unordered-list
         ["questions@circleci.com"])]]
      [:div.clear]]]]))
