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
      [:div#signupform
      "<form id=\"form3\" name=\"form3\" class=\"wufoo topLabel page1\" autocomplete=\"off\" enctype=\"multipart/form-data\" method=\"post\" novalidate=\"\" action=\"http://paulandorla.wufoo.com/forms/interested/#public\">

        <header id=\"header\" class=\"info\">
        <h2>Interested?</h2>
        <div>Get notified when our beta is ready.</div>
        </header>

        <ul>

          <li id=\"fo3li101\" class=\"     \">
          <label class=\"desc\" id=\"title101\" for=\"Field101\">
            Email
            <span id=\"req_101\" class=\"req\">*</span>
          </label>
          <div>
            <input id=\"Field101\" name=\"Field101\" spellcheck=\"false\" class=\"field text medium\" maxlength=\"255\" tabindex=\"1\" onkeyup=\"handleInput(this);\" onchange=\"handleInput(this);\" required=\"\" type=\"email\"> 
          </div>
          </li>



          <li id=\"fo3li1\" class=\"     \">
          <fieldset>
            <!--[if !IE | (gte IE 8)]-->
            <legend id=\"title1\" class=\"desc\">
            </legend>
            <!--[endif]-->
            <!--[if lt IE 8]>
            <label id=\"title1\" class=\"desc\">
            </label>
            <![endif]-->
            <div>
              <span>
                <input id=\"Field1\" name=\"Field1\" class=\"field checkbox\" value=\"May we contact you to ask about your platform/stack/test suite/etc?\" tabindex=\"2\" onchange=\"handleInput(this);\" type=\"checkbox\">
                <label class=\"choice\" for=\"Field1\">May we contact you to ask about your platform/stack/test suite/etc?</label>
              </span>
            </div>
          </fieldset>
          </li>


          <li class=\"buttons \">
          <div>
            <input name=\"currentPage\" id=\"currentPage\" value=\"BbVQyKfGqbsY12gqY0UqbQ6kGoN9nWBKExYDGjFVwuBeNQ=\" type=\"hidden\">

            <input tabindex=\"21\" id=\"saveForm\" name=\"saveForm\" class=\"btTxt submit\" value=\"Submit\" onmousedown=\"doSubmitEvents();\" type=\"submit\">
          </div>
          </li>
        </ul>
      </form> 
    </div><!--container-->
    <img id=\"bottom\" src=\"img/bottom.png\" alt=\"\">

    <style type=\"text/css\">
      @import url(/css/global/power.15346.css);
    </style>

    <!-- JavaScript -->
    <script src=\"js/dynamic.js\"></script>

    <script type=\"text/javascript\">
      __RULES = [];
      __ENTRY = [];
      __PRICES = null;
    </script>"






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
