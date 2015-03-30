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
            [stefon.path :as path]
            [stefon.settings]
            [org.httpkit.server :as httpkit]
            [weasel.repl.websocket :as weasel]))

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

(defn wrap-hosted-scripts
  "Redirects to the canonical url for a hosted script if we're using stefon in development mode."
  [handler stefon-options hosted-scripts]
  (if (not= (:mode stefon-options) :development)
    handler
    (stefon.settings/with-options stefon-options
      (let [paths (set (map :path hosted-scripts))]
        (fn [req]
          (if (or (-> req :uri path/asset-uri? not)
                  (->> req :uri path/uri->adrf (contains? paths) not))
            (handler req)
            (response/redirect (:url (first (filter #(= (:path %)
                                                        (path/uri->adrf (:uri req)))
                                                    hosted-scripts))))))))))

(defonce stopf (atom nil))

(defn stop-server []
  (when-let [f @stopf]
    (println "stopping server")
    (f :timeout 3000)
    (reset! stopf nil)))

(def port 3000)

(def proxy-config
  ;; Incomplete lists of routes to proxy. Unfortuantely duplicated knowledge between
  ;; here and various places in the backend. Ultimately, this will get cleaned up
  ;; once we have production web servers separate from backend API servers.
  {:patterns [;; These are owned by the backend. Add to this list if you expect
              ;; the backend to handle these routes without frontend codebase help.
              #"/api/.*"
              #"/auth/.*"
              #"/cc.xml"
              #"/changelog.rss"
              #"/logout"
              ;; These bootstrap the frontend.  Add to this list if you expect
              ;; the backend to serve HTML to bootstrap the client-side code.
              #"/"
              #"/about"
              #"/about/contact"
              #"/contact"
              #"/account.*"
              #"/add-projects"
              #"/changelog.*"
              #"(?!^/docs/manifest-dev\.json$)^/docs.*$"
              #"/autocomplete.*"
              #"/search-articles.*"
              #"/mobile.*"
              #"/enterprise"
              #"/features"
              #"/features.*"
              #"/gh/.*"
              #"/home"
              #"/integrations.*"
              #"/invite-teammates"
              #"/jobs"
              #"/mobile"
              #"/pricing"
              #"/privacy"
              #"/security.*"
              #"/stories.*"]
   :backends {"dev.circlehost" {:proto "http" :host "circlehost:8080"}
              "prod.circlehost" {:proto "https" :host "circleci.com"}
              "staging.circlehost" {:proto "https" :host "staging.circleci.com"}}})

(defn start-server []
  (stop-server)
  (println "starting server on port" port)
  (reset! stopf
          (httpkit/run-server (-> (site #'routes)
                                  (stefon/asset-pipeline stefon-options)
                                  (wrap-hosted-scripts stefon-options frontend.stefon/hosted-scripts)
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

(defn cljs-repl-env []
  (weasel/repl-env))
