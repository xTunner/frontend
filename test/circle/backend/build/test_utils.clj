(ns circle.backend.build.test-utils
  (:require [circle.env :as env])
  (:require [somnium.congomongo :as mongo])
  (:require [circle.model.build :as build])
  (:require [circle.model.project :as project])
  (:require circle.backend.build.template)
  (:require circle.init)
  (:require [clj-yaml.core :as yaml])
  (:use [circle.util.except :only (eat throw-if-not)])
  (:use [circle.db :only (test-db-connection)])
  (:require [circle.ruby :as ruby])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.sh :as sh])
  (:use [midje.sweet])
  (:import java.io.StringReader))

;; Test repos
(def repo-prefix "test_data/inference/test_dirs")
(defn test-repo [name] (fs/join repo-prefix name))
(def empty-repo (test-repo "empty"))

;; Initialize them here so we know they're always available.
(circle.init/init)

(defn ensure-project [p]
  (when-not (env/production?)
    (throw-if-not (-> p :vcs_url) "vcs_url is required")
    (mongo/destroy! :projects (select-keys p [:vcs_url]))
    (project/insert! p)))

(defn ensure-user [u]
  (when-not (env/production?)
      (mongo/destroy! :users (select-keys u [:email]))
      (mongo/insert! :users u)))

(defn ensure-user-is-project-member [u p]
  (when-not (env/production?)
    (let [pid (-> p ensure-project :_id)
          uid (-> u ensure-user :_id)
          newp (merge p {:user_ids [uid]})
          newu (merge u {:project_ids [pid]})]
      (mongo/update! :projects p newp)
      (mongo/update! :users u newu))))


(defn minimal-node []
  {:username "ubuntu"
   :public-key "ssh-rsa public key"
   :private-key "ssh-rsa private key"
   :keypair-name "test-keypair"})

(def circle-config
  (->
   "nodes:
  www:
    type: node
    ami: ami-a5c70ecc
    instance-type: \"m1.small\"
    username: ubuntu

    ## following only necessary for deployment
    availability-zone: \"us-east-1a\" # customers *could* specify this, probably shouldn't care.
    public-key: \"secret/www.id_rsa.pub\"
    private-key: \"secret/www.id_rsa\"

jobs:
  build:
    template: build
    node: www   # name of node to use. unique per-project
    commands:
      - lein deps:
          pwd: backend
      - lein midje:
          pwd: backend
          environment:
            CIRCLE_ENV: \"staging\"
    #  on-success: deploy # another build to trigger if this one is successful
    notify_emails:
      # Recognized keys, :committer, :owner, or email literals.
      - :committer
      - :owner

  deploy:
    template: deploy
    commands:
      - lein deps:
          pwd: backend
      - lein daemon start \":web\":
          pwd: backend
          environment:
            CIRCLE_ENV: \"production\"
            SWANK: \"true\"
      - sudo /etc/init.d/nginx start

schedule:
  commit:
    job: build
  nightly:
    job: long-tests

    # branches:
    #   only:
    #   exclude:"
   (StringReader.)
   (yaml/parse-string)))

(def circle-github-json
  {:before "93b6b60abe16792a773993b403622d05b7f3ede1",
   :after "0d35a143ced7878d7140a8a1667c8e9f12efb76d",
   :pusher {:email "arohner@gmail.com", :name "arohner"},
   :ref "refs/heads/master",
   :forced false,
   :compare "https://github.com/arohner/CircleCI/compare/7ef45f9...9538736",
   :created false,
   :commits [{:message "Rename", :distinct true, :id "0d35a143ced7878d7140a8a1667c8e9f12efb76d", :url "https://github.com/arohner/CircleCI/commit/0d35a143ced7878d7140a8a1667c8e9f12efb76d", :timestamp "2011-11-16T12:44:52-08:00", :author {:email "arohner@gmail.com", :username "arohner", :name "Allen Rohner"}, :removed ["circleci.yml"], :modified [], :added ["circle.yml"]}],
   :repository
   {:has_issues true,
    :fork false,
    :pushed_at "2011/11/16 12:44:58 -0800",
    :name "CircleCI",
    :watchers 2,
    :has_wiki true,
    :owner {:email "arohner@gmail.com", :name "arohner"},
    :language "Clojure",
    :size 1368,
    :created_at "2011/09/06 15:51:21 -0700",
    :private true,
    :homepage "",
    :url "https://github.com/arohner/CircleCI",
    :has_downloads true,
    :open_issues 0,
    :forks 0,
    :description ""},
   :deleted false})

(def circle-dummy-project-json-str "{\"before\":\"7802df3088473d8d88e0f5481d294608cc6facdd\",\"after\":\"78f58846a049bb6772dcb298163b52c4657c7d45\",\"pusher\":{\"name\":\"none\"},\"ref\":\"refs/heads/master\",\"forced\":false,\"compare\":\"https://github.com/arohner/circle-dummy-project/compare/7802df3...78f5884\",\"created\":false,\"commits\":[{\"timestamp\":\"2011-12-12T22:35:49-08:00\",\"author\":{\"email\":\"arohner@gmail.com\",\"username\":\"arohner\",\"name\":\"Allen Rohner\"},\"removed\":[],\"added\":[\"dummy.id_rsa\"],\"url\":\"https://github.com/arohner/circle-dummy-project/commit/78f58846a049bb6772dcb298163b52c4657c7d45\",\"message\":\"Add SSH keys\",\"modified\":[],\"distinct\":true,\"id\":\"78f58846a049bb6772dcb298163b52c4657c7d45\"}],\"repository\":{\"has_issues\":true,\"fork\":false,\"pushed_at\":\"2011/12/12 22:36:45 -0800\",\"name\":\"circle-dummy-project\",\"watchers\":1,\"has_wiki\":true,\"owner\":{\"email\":\"arohner@gmail.com\",\"name\":\"arohner\"},\"size\":96,\"created_at\":\"2011/12/12 22:32:31 -0800\",\"private\":true,\"homepage\":\"\",\"url\":\"https://github.com/arohner/circle-dummy-project\",\"has_downloads\":true,\"open_issues\":0,\"forks\":0,\"description\":\"\"},\"deleted\":false}")

(def circle-dummy-project-json
  {:before "7802df3088473d8d88e0f5481d294608cc6facdd",
   :after "78f58846a049bb6772dcb298163b52c4657c7d45",
   :pusher {:name "none"},
   :ref "refs/heads/master",
   :forced false,
   :compare "https://github.com/arohner/circle-dummy-project/compare/7802df3...78f5884",
   :created false,
   :commits [{:message "Add SSH keys", :distinct true, :id "78f58846a049bb6772dcb298163b52c4657c7d45", :url "https://github.com/arohner/circle-dummy-project/commit/78f58846a049bb6772dcb298163b52c4657c7d45", :timestamp "2011-12-12T22:35:49-08:00", :author {:email "arohner@gmail.com", :username "arohner", :name "Allen Rohner"}, :removed [], :modified [], :added ["dummy.id_rsa"]}],
   :repository {:has_issues true, :fork false, :pushed_at "2011/12/12 22:36:45 -0800", :name "circle-dummy-project", :watchers 1, :has_wiki true, :owner {:email "arohner@gmail.com", :name "arohner"}, :size 96, :created_at "2011/12/12 22:32:31 -0800", :private true, :homepage "", :url "https://github.com/arohner/circle-dummy-project", :has_downloads true, :open_issues 0, :forks 0, :description ""},
   :deleted false})

(def circle-project
  (project/project
    :name "CircleCI",
    :vcs_url "https://github.com/arohner/CircleCI",
    :vcs_type "git",
    :lb-name "www"
    :ssh_private_key ""))

(def test-project
  (project/project
    :name "Dummy Project",
    :vcs_url "https://github.com/arohner/circle-dummy-project",
    :vcs_type "git",
    :lb-name "www"
    :ssh_private_key ""))

(def partially-inferred-project
  (project/project
   :name "Dummy Project",
   :vcs_url "https://github.com/arohner/circle-empty-repo",
   :vcs_type "git",
   :test "echo a\necho b"
   :ssh_private_key ""
   :ssh_public_key "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCI41xErx6O8WKo6Z3upop9XKhI/gi93Za0vo/LKBc0Wo6b/bEIwh37HcRp8ijaNF/D+4wj7OxyBIi70SdARE8JqxOKZwNZx+NrN/ojdNfA7Ggv7JyZSOOGXK+mfzcuCshD4yIqWQmj5zcPjsLwz6AYVk3YCAD4LQFJ0usveK56Ew== \n"))

(def test-user
  {:email "user@test.com"})

(defn ensure-test-user []
  (ensure-user test-user))

(defn ensure-test-user-and-project []
  (ensure-project test-project)
  (ensure-test-user)
  (ensure-user-is-project-member test-user test-project))

(def circle-request
  {:remote-addr "127.0.0.1",
   :scheme :http,
   :query-params {},
   :session {},
   :form-params {"payload" "{\"pusher\":{\"name\":\"none\"},\"repository\":{\"name\":\"CircleCI\",\"created_at\":\"2011/09/06 15:51:21 -0700\",\"size\":1420,\"has_wiki\":true,\"watchers\":2,\"private\":true,\"language\":\"Clojure\",\"url\":\"https://github.com/arohner/CircleCI\",\"fork\":false,\"pushed_at\":\"2011/11/18 09:12:29 -0800\",\"has_downloads\":true,\"open_issues\":0,\"homepage\":\"\",\"has_issues\":true,\"description\":\"\",\"forks\":0,\"owner\":{\"name\":\"arohner\",\"email\":\"arohner@gmail.com\"}},\"forced\":false,\"after\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"deleted\":false,\"ref\":\"refs/heads/master\",\"commits\":[{\"added\":[\"backend/src/circle/util/coerce.clj\"],\"modified\":[],\"removed\":[],\"author\":{\"name\":\"Allen Rohner\",\"username\":\"arohner\",\"email\":\"arohner@gmail.com\"},\"timestamp\":\"2011-11-18T09:12:23-08:00\",\"url\":\"https://github.com/arohner/CircleCI/commit/587ae3917fc8e6a9f62e49a276d5572863540410\",\"id\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"distinct\":true,\"message\":\"Add missing file\"}],\"before\":\"a885fe29a55a44c8b25e833b1dedf28e9cf3a1a4\",\"compare\":\"https://github.com/arohner/CircleCI/compare/a885fe2...587ae39\",\"created\":false}"},
   :multipart-params {},
   :request-method :post,
   :query-string nil,
   :content-type "application/x-www-form-urlencoded",
   :cookies {},
   :uri "/github-commit",
   :server-name "www.circleci.com",
   :params {:payload "{\"pusher\":{\"name\":\"none\"},\"repository\":{\"name\":\"CircleCI\",\"created_at\":\"2011/09/06 15:51:21 -0700\",\"size\":1420,\"has_wiki\":true,\"watchers\":2,\"private\":true,\"language\":\"Clojure\",\"url\":\"https://github.com/arohner/CircleCI\",\"fork\":false,\"pushed_at\":\"2011/11/18 09:12:29 -0800\",\"has_downloads\":true,\"open_issues\":0,\"homepage\":\"\",\"has_issues\":true,\"description\":\"\",\"forks\":0,\"owner\":{\"name\":\"arohner\",\"email\":\"arohner@gmail.com\"}},\"forced\":false,\"after\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"deleted\":false,\"ref\":\"refs/heads/master\",\"commits\":[{\"added\":[\"backend/src/circle/util/coerce.clj\"],\"modified\":[],\"removed\":[],\"author\":{\"name\":\"Allen Rohner\",\"username\":\"arohner\",\"email\":\"arohner@gmail.com\"},\"timestamp\":\"2011-11-18T09:12:23-08:00\",\"url\":\"https://github.com/arohner/CircleCI/commit/587ae3917fc8e6a9f62e49a276d5572863540410\",\"id\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"distinct\":true,\"message\":\"Add missing file\"}],\"before\":\"a885fe29a55a44c8b25e833b1dedf28e9cf3a1a4\",\"compare\":\"https://github.com/arohner/CircleCI/compare/a885fe2...587ae39\",\"created\":false}"},
   :headers {"x-real-ip" "10.248.202.174", "x-github-event" "push", "accept" "*/*", "host" "www.circleci.com", "x-forwarded-proto" "http", "x-forwarded-for" "207.97.227.253, 10.112.43.159, 10.248.202.174", "content-type" "application/x-www-form-urlencoded", "content-length" "1620", "connection" "close", "x-forwarded-port" "80"},
   :content-length 1620,
   :server-port 80,
   :character-encoding nil,
   ;; :body #<Input org.mortbay.jetty.HttpParser$Input@1579f4e>  real class has this, but it's an unreadable form.
   })

(defn localhost-name []
  (clojure.java.shell/sh "hostname"))

(defn ensure-localhost-ssh []
  "Ensure we're able to SSH into localhost, if this is a CI box."
  (let [home-dir (System/getenv "HOME")]
    (when (and (ec2/self-instance-id)
             (not (fs/exists? (fs/join home-dir ".ssh/id_rsa"))))
    (let [keys (ssh/generate-keys)]
      (spit (fs/join home-dir ".ssh/id_rsa") (-> keys :private-key))
      (spit (fs/join home-dir ".ssh/id_rsa.pub") (-> keys :public-key))
      (sh/sh (format "echo '%s' >> %s" (-> keys :public-key) (fs/join home-dir ".ssh/authorized_keys")))))))

(defn localhost-ssh-map
  "Returns a node-spec that connects to localhost instead of a remote
  box. Can be passed to (minimal-build) in the :node key"
  []
  (ensure-localhost-ssh)
  (let [username (System/getenv "USER")
        ssh-dir (format "%s/.ssh" (System/getProperty "user.home"))]
    (assert username)
    {:username username
     :ip-addr "localhost"
     :public-key (or (eat (slurp (format "%s/id_rsa.pub" ssh-dir)))
                     (eat (slurp (format "%s/id_dsa.pub" ssh-dir))))
     :private-key (or (eat (slurp (format "%s/id_rsa" ssh-dir)))
                      (eat (slurp (format "%s/id_dsa" ssh-dir))))
     :keypair-name "local-keys"}))

(defn minimal-build [& {:keys [_id
                               build_num
                               actions
                               notify_emails
                               node
                               template]
                        :as args}]
  (let [actions (or actions [])
        actions (if template
                  (circle.backend.build.template/apply-template template actions)
                  actions)]
    (build/build (merge args
                        {:vcs_url (-> test-project :vcs_url)
                         :vcs_revision "1234567890foobar"
                         :node (or node (localhost-ssh-map))
                         :notify_emails (or notify_emails [])
                         :actions actions
                         :subject "dummy commit message"}))))

(defn clear-test-db []
  (mongo/with-mongo (test-db-connection)
    (mongo/drop-database! :mongoid_test_test)))

(defn ensure-test-db []
  (clear-test-db)
  (mongo/with-mongo (test-db-connection)
    (ensure-project test-project)
    (ensure-project circle-project)
    (ensure-project partially-inferred-project)
    (ensure-test-user-and-project)))

(defmacro test-ns-setup
  "Defines a midje (background) such that the test DB is cleared
  between runs, and all clojure DB connections go through the test DB"
  []
  `(background (before :facts (ensure-test-db))
               (around :facts (ruby/with-runtime (ruby/test-ruby)
                                (mongo/with-mongo (test-db-connection)
                                  ?form)))))
