(ns circle.workers.github
  (:require [org.danlarkin.json :as json])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.run :as run])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-http.client :as client])
  (:require [circle.env :as env])
  (:require [somnium.congomongo :as mongo])
  (:require [tentacles.repos :as trepos])
  (:require [tentacles.users :as tusers])
  (:require [tentacles.orgs :as torgs])
  (:require [circle.backend.ssh :as ssh])
  (:use [midje.sweet]))

(defn process-json [github-json]
  (when (= "CircleCI" (-> github-json :repository :name))
    (let [build (circle/circle-build)]
      (dosync
       (alter build merge
              {:vcs_url (->ssh (-> github-json :repository :url))
               :repository (-> github-json :repository)
               :commits (-> github-json :commits)
               :vcs_revision (-> github-json :commits last :id)
               :num-nodes 1}))
      (infof "process-json: build: %s" @build)
      (run/run-build build))))

(defn start-build-from-hook
  [url after ref json-string]
  (-> json-string json/decode process-json))

;;; https://github.com/account/applications/4808
(def production-github {:client_id "78a2ba87f071c28e65bb"
                        :client_secret "98cb9262b67ad26bed9191762a23445eeb2054e4"})

;;; https://github.com/account/applications/5042
(def staging-github {:client_id "593c08e3973f3e534013"
                     :client_secret "4f193c232eb94a9ae7bcf1c495a8a3e805dc3493"})

;;; https://github.com/account/applications/4814
(def local-github {:client_id "586bf699b48f69a09d8c"
                   :client_secret "1e93bdce2246fd69d9040875338b4137d525e400"})

(defn default []
  (cond
   env/production? production-github
   env/staging? staging-github
   env/local? local-github))

(def settings (default))

; TECHNICAL_DEBT upstream this
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
                                                    :scope "repo"
                                                    :redirect_uri redirect})]
    (str endpoint "?" query-string)))

(fact "authorization-url works"
  (-> "http://localhost:3000/hooks/repos" authorization-url) =>
  "https://github.com/login/oauth/authorize?client_id=586bf699b48f69a09d8c&scope=repo&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fhooks%2Frepos")

(defn add-user-details [userid]
  (let [user (mongo/fetch-by-id :users (mongo/object-id userid))
        token (:github_access_token user)
        json (tentacles.users/me {:oauth_token token})
        email (-> json :email)
        name (-> json :name)
        new-user (merge user {:fetched_name name :fetched_email email})]
    (mongo/update! :users user new-user)))


(defn add-deploy-key
  "Given a username/repo pair, like 'arohner/CircleCI', generate and install a deploy key"
  [username repo-name github_access_token project-id]
  (let [project (mongo/fetch-by-id :projects (mongo/object-id project-id))
        keypair (ssh/generate-keys)]
    (mongo/update! :projects project (merge project {:ssh_public_key (-> keypair :public-key)
                                                     :ssh_private_key (-> keypair :private-key)}))
                                        ;TECHNICAL_DEBT make sure this throws exceptions. 0.1.1-64e42ffb78a740de3a955b6b66cc6d86905609a5 does not
    (trepos/create-key username repo-name "Circle continuous integration" (-> keypair :public-key) {:oauth_token github_access_token})))

(defn add-hooks
  "Add all the hooks we care about to the user's repo"
  [username reponame github-access-token]
  (trepos/create-hook username reponame "web" {:url "www.circleci.com/hooks/github"} {:oauth_token github-access-token}))

(defn all-repos
  "Returns all repos the current user can access. Use this over tentacles.repos/repos, because this will return repos belonging to organizations as well"
  [github-access-token]
  (let [normal-repos (future (trepos/repos {:oauth_token github-access-token}))
        orgs (future (torgs/orgs {:oauth_token github-access-token}))
        org-repos (doall (map (fn [o]
                                (future (torgs/repos (-> o :login) {:oauth_token github-access-token}))) @orgs))]
    (concat @normal-repos (mapcat deref org-repos))))