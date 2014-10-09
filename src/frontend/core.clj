(ns frontend.core
  (:require [cheshire.core :as json]
            [compojure.core :refer (defroutes GET ANY)]
            [compojure.handler :refer (site)]
            [compojure.route]
            [frontend.less :as less]
            [frontend.util.docs :as doc-utils]
            [ring.util.response :as response]
            [stefon.core :as stefon]
            [org.httpkit.server :as httpkit]))

(def stefon-options
  {:asset-roots ["resources/assets"]
   :mode :development})

(defroutes routes
  (compojure.route/resources "/" {:root "public"
                                  :mime-types {:svg "image/svg"}})
  (GET "/docs/manifest-dev.json" []
       (-> (doc-utils/read-doc-manifest "resources/assets/docs")
           (json/encode {:pretty true})
           (response/response)
           (response/content-type "application/json")))
  (ANY "*" [] {:status 404 :body nil}))

(defn cross-origin-everything
  "This lets us use local assets without the browser complaining. Safe because
   we're only serving assets in dev-mode from here."
  [handler]
  (fn [req]
    (-> (handler req)
        (response/header "Access-Control-Allow-Origin" "*"))))

(defn -main
  "Starts the server that will serve the assets when visiting circle with ?use-local-assets=true"
  []
  (println "Starting less compiler.")
  (less/init)
  (println "starting server on port 8080")
  (httpkit/run-server (-> (site #'routes)
                          (stefon/asset-pipeline stefon-options)
                          (cross-origin-everything))
                      {:port 8080}))
