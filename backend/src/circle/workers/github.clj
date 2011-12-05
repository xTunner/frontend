(ns circle.workers.github
  (:require [org.danlarkin.json :as json])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.run :as run])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-http.client :as client])
  (:require [circle.env :as env])
  (:require [somnium.congomongo :as mongo])
  (:use [midje.sweet]))

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


(def production-github {:client_id "78a2ba87f071c28e65bb"
                        :client_secret "98cb9262b67ad26bed9191762a23445eeb2054e4"})

(def staging-github {:client_id "TODO"
                     :client_secret "TODO"})

(def local-github {:client_id "586bf699b48f69a09d8c"
                   :client_secret "1e93bdce2246fd69d9040875338b4137d525e400"})

(defn default []
  (cond
   env/production? production-github
   env/staging? staging-github
   env/local? local-github))

(def settings (default))



(defn fetch-github-access-token [userid code]
  "After sending a customer to github to provide us with oauth permission, github
  redirects them back, providing us with a temporary code. We can use this code to ask
  github for an access token."
  (let [response (client/post "https://github.com/login/oauth/access_token"
                              {:form-params (assoc settings :code code)
                               :accept :json})
        json (-> response :body json/decode)
        access-token (-> json :access_token)
        error? (-> json :error)]
    (if error?
      false ; TODO: we don't have a good way of raising errors to our
                     ; attention. Use Airbrake.
      (let [user (mongo/fetch-by-id :users (mongo/object-id userid))
            updated (merge user {:github_access_token access-token})]
        (assert user)
        (mongo/update! :users user updated)
        true))))

(defn authorization-url [redirect]
  "The URL that we send a user to, to allow them authorize us for oauth. Redirect is where the should be redirected afterwards"
  (let [endpoint "https://github.com/login/oauth/authorize"
        query-string (client/generate-query-string {:client_id (:client_id settings)
                                                    :scopes "repo"
                                                    :redirect_uri redirect})]
    (str endpoint "?" query-string)))


(fact "authorization-url works"
  (-> "http://localhost:3000/hooks/repos" authorization-url) =>
  "https://github.com/login/oauth/authorize?client_id=586bf699b48f69a09d8c&scopes=repo&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fhooks%2Frepos")
