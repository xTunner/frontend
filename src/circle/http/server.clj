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
     [:script {:src (dieter/link-to-asset "login.hamlc" dieter-options)}]
     [:script "var github_url='" (github/authorization-url "/") "';"]]

    [:body
     [:div {:id "all"}]
     [:script "$(window.document).ready(function() { $('#all').html(HAML['login']({}));});"]]]))
