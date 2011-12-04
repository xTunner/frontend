(ns circle.workers.github
  (:require [org.danlarkin.json :as json])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.run :as run])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-http.client :as client])
  (:require [circle.env :as env]))

(defn process-json [github-json]
  (when (= "CircleCI" (-> github-json :repository :name))
    (let [build (circle/circle-build)]
      (dosync
       (alter build merge
              {:vcs-url (->ssh (-> github-json :repository :url))
               :repository (-> github-json :repository)
               :commits (-> github-json :commits)
               :vcs-revision (-> github-json :commits last :id)
               :num-nodes 1}))
      (infof "process-json: build: %s" @build)
      (run/run-build build))))

(defn start-build-from-hook
  [url after ref json-string]
  (-> json-string json/decode process-json))


(def production-github {:client-id "78a2ba87f071c28e65bb"
                        :secret "98cb9262b67ad26bed9191762a23445eeb2054e4"})

(def staging-github {:client-id "TODO"
                     :secret "TODO"})

(def local-github {:client-id "586bf699b48f69a09d8c"
                   :secret "1e93bdce2246fd69d9040875338b4137d525e400"})

(defn default []
  (cond
   env/production? production-github
   env/staging? staging-github
   env/local? local-github))

(def settings (default))

(defn fetch-github-access-token [code]
  "After sending a customer to github to provide us with oauth permission, github
  redirects them back, providing us with a temporary code. We can use this code to ask
  github for an access token."
  (let [response (client/post "https://github.com/login/oauth/access_token"
                              {:form-params (assoc settings :code code)
                               :accept :json})]
    (-> response :body json/decode :access_token)))
