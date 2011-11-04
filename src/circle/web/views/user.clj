(ns circle.web.views.user
  (:use noir.core
        circle.web.views.common
        hiccup.page-helpers
        hiccup.form-helpers))

(defpage "/user/:username" {:keys [username]}
  (layout {}
          [:div#pitch_wrap
           [:div#pitch "Not much here yet."]]
          [:div#content_wrap
           [:div#content "Not much here yet." "username=" username]]))