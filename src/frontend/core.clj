(ns frontend.core
  (:require [cheshire.core :as json]
            [compojure.core :refer (defroutes GET ANY)]
            [compojure.handler :refer (site)]
            [compojure.route]
            [frontend.less :as less]
            [frontend.util.docs :as doc-utils]
            [frontend.proxy :as proxy]
            [frontend.stefon]
            [ring.util.response :as response]
            [stefon.core :as stefon]
            [org.httpkit.server :as httpkit]))

(def stefon-options
  {:asset-roots frontend.stefon/asset-roots
   :mode :development})

(defroutes routes
  (GET "/js/om-dev.js" []
       (response/redirect (stefon/link-to-asset "js/om-dev.js.stefon" stefon-options)))
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

(defonce stopf (atom nil))

(defn stop-server []
  (when-let [f @stopf]
    (println "stopping server")
    (f :timeout 3000)
    (reset! stopf nil)))

(def port 3000)

(def proxy-config
  {:patterns [#"/"
              #"/changelog.rss"
              #"/logout"
              #"/auth/.*"
              #"/api/.*"]
   :backends {"dev.circlehost" {:proto "http" :host "circlehost:8080"}
              "prod.circlehost" {:proto "https" :host "circleci.com"}
              "staging.circlehost" {:proto "https" :host "staging.circleci.com"}}})

(defn start-server []
  (stop-server)
  (println "starting server on port" port)
  (reset! stopf
          (httpkit/run-server (-> (site #'routes)
                                  (stefon/asset-pipeline stefon-options)
                                  (proxy/wrap-handler proxy-config)
                                  (cross-origin-everything))
                              {:port port}))
  nil)

(defn -main
  "Starts the server that will serve the assets when visiting circle with ?use-local-assets=true"
  []
  (println "Starting less compiler.")
  (less/init)
  (start-server))
