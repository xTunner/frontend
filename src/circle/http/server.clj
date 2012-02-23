(ns circle.http.server
  (:use [circle.util.core :only (defn-once)])
  (:require [clojure.pprint :as pprint])
  (:use [clojure.tools.logging :only (infof)])

  (:use noir.core)
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


(defn start []
  (server/add-middleware (var logging))
  (def server (server/start (port))))

(defn stop []
  (swap! noir.server.handler/middleware (constantly []))
  (server/stop server))

(defn restart []
  (stop)
  (start))

(defn-once init
  (start))


(defn thepage []
  (clojure.string/escape (slurp "resources/public/login.hamlc") {\' "\\'" \newline "\\n" \return "\\r"}))

(defpage "/" []
  (html
   [:html
    [:head
     [:script {:src "hamlcoffee.min.js"}]
     [:script {:src "http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js"}]
     [:script "var github_url='" (github/authorization-url "/") "';"]
     [:script "var hamlc = require('haml-coffee');
               tmpl = hamlc.compile('" (thepage) "');
               html = tmpl({});
               alert(html);
               $(window.document).html(html);"]]]))
