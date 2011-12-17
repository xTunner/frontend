(ns circle.web.views.posterous
  "Produces the posterous theme"
  (:use [noir.core :only (defpartial)])
  (:use [circle.web.views.common :only (layout)]))

(declare posterous)

;;; How to use this: At the repl, call (render). Paste the output into Posterous's advanced theming box. Hit save.

(defn render []
  (let [absolute-url "https://circleci.com/"
        path "resources/posterous_theme.html"]
    (hiccup.core/with-base-url absolute-url
      (print (posterous)))))

(defpartial posterous []
  (layout {}
   [:div#page_title_wrap
    [:div#page_title
     [:h3 "The Circle Blog"]]
    [:div.clear]]
   [:div#content_wrap
    [:div#content
     [:div#main_content_wide_wpadding.left
      "{block:Posts}"
      [:article {:class "post clearfix"
                 :id "post_{PostID"}
       [:header
        [:section
         "{block:NewDayDate}"
         [:time {:datetime="{Year}-{MonthNumberWithZero}-{DayOfMonthWithZero}" :pubdate="pubdate"}
          [:a {:href "{Permalink}"} "{Month} {DayOfMonth}, {Year}"]]
         "{/block:NewDayDate}"]
        [:h2
         [:a {:href "{Permalink}"}]
         "{Title}"]]
       [:div.body
        [:div.inner
         "{Body}"]]
       [:footer
        "{block:List}"
        "{block:TagList}"
        [:section.tags
         [:h1 "Filed under" [:span "&nbsp"]]
         [:ul
          "{block:TagListing}"
          "<li><a href=\"{TagLink}\" rel=\"tag\">{TagName}</a></li>"
          "{/block:TagListing}"]]
        "{/block:TagList}"
        "{/block:List}"
        "{block:Responses}
           {block:ResponsesShow}
            {ResponseWidget}
            {Responses}
	    {ResponseForm}
           {/block:ResponsesShow}
          {/block:Responses}"]]
      "{/block:Posts}"]
     "{block:List}
        <footer id= \"pagination\" >
          {block:Pagination/}
        </footer>
      {/block:List}"]]))
