(ns circle.workers.github
  (:require [cheshire.core :as json])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.model.build :as build])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.config :as config])
  (:require [circle.model.project :as project])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-http.client :as client])
  (:require [circle.env :as env])
  (:require [somnium.congomongo :as mongo])
  (:require [circle.mongo :as c-mongo])
  (:require [tentacles.repos :as trepos])
  (:require [tentacles.users :as tusers])
  (:require [tentacles.orgs :as torgs])
  (:require [circle.backend.ssh :as ssh]))

(defn process-json [github-json]
  (infof "build-hook: %s" github-json)
  (when-not (= (-> github-json :after) "0000000000000000000000000000000000000000")
    (let [build (config/build-from-json github-json)
          project (build/get-project build)]
      (if (project/enabled? project)
        (do
          (println "process-json: running build: %s" @build)
          (run/run-build build))
        (println "process-json: not running build: %s" @build)))))

(defn start-build-from-hook
  [json-string]
  (-> json-string (json/parse-string true) (process-json)))

;;; TECHNICAL_DEBT: These are on pbiggar's account, we should probably change
;;; that at some point.

;;; https://github.com/account/applications/4808
(def production-github {:client_id "78a2ba87f071c28e65bb"
                        :client_secret "98cb9262b67ad26bed9191762a23445eeb2054e4"})

;;; https://github.com/account/applications/5042
(def staging-github {:client_id "593c08e3973f3e534013"
                     :client_secret "4f193c232eb94a9ae7bcf1c495a8a3e805dc3493"})

;;; https://github.com/account/applications/5518
;;; circlehost:3000
(def development-github {:client_id "383faf01b98ac355f183"
                         :client_secret "7efaf34f808af2fee26c5532f0ceafd2c475a1ce"})

;;; https://github.com/account/applications/4818
;;; circlehost:3001
(def test-github {:client_id "586bf699b48f69a09d8c"
                   :client_secret "1e93bdce2246fd69d9040875338b4137d525e400"})

(defn default []
  (cond
   (env/production?) production-github
   (env/staging?) staging-github
   (env/development?) development-github
   (env/test?) test-github))


(defn settings []
  (default))

; TECHNICAL_DEBT upstream this
(defn fetch-github-access-token [userid code]
  "After sending a customer to github to provide us with oauth permission, github
  redirects them back, providing us with a temporary code. We can use this code to ask
  github for an access token."
  (let [response (client/post "https://github.com/login/oauth/access_token"
                              {:form-params (assoc (settings) :code code) :accept :json})
        json (-> response :body (json/parse-string true))
        access-token (-> json :access_token)
        error? (-> json :error)]
    (when error?
      (throw error?))
    (c-mongo/set :users userid :github_access_token access-token)
    true))


(defn authorization-url [redirect]
  "The URL that we send a user to, to allow them authorize us for oauth. Redirect is where the should be redirected afterwards"
  (let [endpoint "https://github.com/login/oauth/authorize"
        query-string (client/generate-query-string {:client_id (:client_id (settings))
                                                    :scope "repo"
                                                    :redirect_uri redirect})]
    (str endpoint "?" query-string)))

(defn add-user-details [userid]
  (let [user (mongo/fetch-by-id :users (mongo/object-id userid))
        token (:github_access_token user)
        json (tentacles.users/me {:oauth_token token})
        email (-> json :email)
        name (-> json :name)]
    (c-mongo/set :users userid :fetched_name name :fetched_email email)))


(defn add-deploy-key
  "Given a username/repo pair, like 'arohner/CircleCI', generate and install a deploy key"
  [username repo-name github_access_token project-id]
  (let [keypair (ssh/generate-keys)]
    (c-mongo/set :projects
                 project-id
                 :ssh_public_key (-> keypair :public-key)
                 :ssh_private_key (-> keypair :private-key))
    ;; TECHNICAL_DEBT make sure this throws exceptions. 0.1.1-64e42ffb78a740de3a955b6b66cc6d86905609a5 does not
    (trepos/create-key username repo-name "Circle continuous integration" (-> keypair :public-key) {:oauth_token github_access_token})))

(def circle-hook-url "www.circleci.com/hooks/github")

(defn circle-hooks [username reponame github-access-token]
  (->> (trepos/hooks username reponame {:oauth_token github-access-token})
       (filter #(and (= "web" (-> % :name)) (= circle-hook-url (-> % :config :url))))))

(defn has-circle-hook? [username reponame github-access-token]
  (pos? (count (circle-hooks username reponame github-access-token))))

(defn add-hooks
  "Add all the hooks we care about to the user's repo"
  [username reponame github-access-token]
  (trepos/create-hook username reponame "web" {:url circle-hook-url} {:oauth_token github-access-token}))

(defn ensure-hooks [username reponame github-access-token]
  (if (has-circle-hook? username reponame github-access-token)
    (infof "hook already present on %s/%s, not adding" username reponame)
    (add-hooks username reponame github-access-token)))

(defn delete-hook [{:keys [username reponame github-access-token hook-id] :as args}]
  (println "delete-hook:" args)
  (trepos/delete-hook username reponame hook-id {:oauth_token github-access-token}))

(defn cleanup-hooks []
  (doseq [p (mongo/fetch :projects)]
    (let [{:keys [username project]} (circle.backend.github-url/parse (-> p :vcs_url))
          user-id (-> p :user_ids (first))
          user (mongo/fetch-one :users :where {:_id user-id})
          github-access-token (-> user :github_access_token)
          circle-hooks (circle-hooks username project github-access-token)
          num-hooks (count circle-hooks)]
      (when (> num-hooks 1)
        (println (-> p :vcs_url) "has" num-hooks "circle hooks")
        (doseq [h (rest circle-hooks)]
          (delete-hook {:username username :reponame project :github-access-token github-access-token :hook-id (-> h :id)})))
      (when (== 0 num-hooks)
        (println (-> p :vcs_url) ":NO HOOK")
        (add-hooks username project github-access-token)))))

(def default-repo-map  {
   :url "https://api.github.com/repos/octocat/Hello-World"
   :html_url "https://github.com/octocat/Hello-World"
   :clone_url "https;//github.com/octocat/Hello-World.git"
   :git_url "git://github.com/octocat/Hello-World.git"
   :ssh_url "git@github.comoctocat/Hello-World.git"
   :svn_url "https://svn.github.com/octocat/Hello-World"
   :owner { :login "octocat"
             :id 1
             :avatar_url "https://github.com/images/error/octocat_happy.gif"
             :gravatar_id "somehexcode"
             :url "https://api.github.com/users/octocat"}
   :name "Hello-World"
   :description "No description provided"
   :homepage "https://github.com"
   :language "unknown"
   :private false
   :fork false
   :forks 0
   :watchers 0
   :size 1
   :master_branch "master"
   :open_issues 0
   :pushed_at "1970-01-01T00:00:00Z"
   :created_at "1970-01-01T00:00:00Z"})

(defn all-repos
  "Returns all repos the current user can access. Use this over tentacles.repos/repos, because this will return repos belonging to organizations as well"
  [github-access-token]
  (let [normal-repos (future (trepos/repos {:oauth_token github-access-token}))
        orgs (future (torgs/orgs {:oauth_token github-access-token}))
        org-repos (doall (map (fn [o]
                                (future (torgs/repos (-> o :login) {:oauth_token github-access-token}))) @orgs))
        all (concat @normal-repos (mapcat deref org-repos))
        all (map
             (fn [repo]
               (merge-with ;; don't overwrite the defaults with nil
                (fn [first second]
                  (if (nil? second)
                    first
                    second))
                default-repo-map repo))
             all)]
    all))

(defn api-key-for-project [project-url]
  (let [project (mongo/fetch-one :projects :where {:vcs_url project-url})
        _ (println (-> project :user_ids (first)))
        user (mongo/fetch-one :users :where {:_id (-> project :user_ids (first))})]
    (-> user :github_access_token)))