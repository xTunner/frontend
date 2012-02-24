(ns circle.http.server
  (:use [circle.util.core :only (defn-once)])
  (:require [clojure.pprint :as pprint])
  (:use [clojure.tools.logging :only (infof)])

  (:use noir.core)
  (:require [dieter.core :as dieter])
  (:use [hiccup.core :only (html)])
  (:require [noir.response :as response])
  (:require [noir.server :as server])

  (:require [circle.workers.github :as github]))


(defn logging [handler]
  (fn [request]
    (infof "request: \n%s\n\n" (with-out-str (pprint/pprint request)))
    (let [resp (handler request)]
      (infof "resp:\n%s\n\n"  (with-out-str (pprint/pprint resp)))
      resp)))


(defn port []
  (if (circle.env/test?)
    8081
    8080))

(def dieter-options
  (if (circle.env/development?)
    {:compress false
     :asset-root "resources"
     :cache-root "resources/asset-cache"
     :cache-mode :development}
    {:compress true
     :asset-root "resources"
     :cache-root "resources/asset-cache"
     :cache-mode :production}))

(defn asset [file]
  (dieter/link-to-asset file dieter-options))

(defn start []
  (server/add-middleware (var logging))
  (server/add-middleware dieter/asset-pipeline dieter-options)
  (def server (server/start (port))))

(defn stop []
  (swap! noir.server.handler/middleware (constantly []))
  (server/stop server))

(defn restart []
  (stop)
  (start))

(defn-once init
  (start))


(defpage "/" []
  (html
   [:html
    [:head
     [:script {:src "http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js"}]

     [:link {:rel "stylesheet" :type "text/css" :href (asset "css/master.css")}]
     [:link {:href "http://fonts.googleapis.com/css?family=Open+Sans:400italic,600italic,400,600" :rel "stylesheet" :type "text/css" }]

     [:script {:src (asset "js/modernizr-2.0.6.min.js")}]
     [:script {:src (asset "views/login.hamlc")}]
     [:script {:src (asset "js/jquery.placeholder.js")}]
     [:script "$(window.document).ready(function() { $('body').html(HAML['login']({})); $('body').attr('id', 'login-wrapper');});"]]

    [:body]]))
