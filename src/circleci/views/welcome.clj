(ns circleci.views.welcome
  (:require [circleci.views.common :as common]
            [noir.content.pages :as pages])
  (:use noir.core
        hiccup.core
        hiccup.page-helpers)
  (:use [ring.util.response :only (file-response)]))

(defpage "/" []
  (file-response "resources/public/html/index.html"))
