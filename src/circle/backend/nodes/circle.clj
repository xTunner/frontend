(ns circle.backend.nodes.circle
  (:require pallet.core
            pallet.phase
            [pallet.action.user :as user]
            [pallet.action.directory :as directory]
            [pallet.action.package :as package]
            [pallet.action.remote-file :as remote-file]
            [pallet.action.service :as service]
            [pallet.crate.automated-admin-user :as automated-admin-user]
            [pallet.crate.git :as git]
            [pallet.crate.lein :as lein]
            [pallet.crate.java :as java]
            [pallet.crate.ssh-key :as ssh-key]
            [pallet.crate.network-service :as network-service]
            [pallet.crate.postgres :as postgres]
            [pallet.crate.nginx :as nginx]
            [pallet.thread-expr :as thread-expr]
            [circle.backend.nodes :as nodes]))


;; this is our "memoized" circle box
(def circle-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-afa46dc6"
                      :availability-zone "us-east-1a"
                      :instance-type "m1.small"
                      :keypair-name "www"
                      :security-groups ["www" "allow-DB"]
                      :username "ubuntu"
                      :public-key (slurp "www.id_rsa.pub")
                      :private-key (slurp "www.id_rsa")}))

;; The configuration to build the circle box from scratch
(def circle-raw-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-06ad526f" ;; clean ubuntu 11.10
                      :availability-zone "us-east-1a"
                      :instance-type "m1.small"
                      :keypair-name "www"
                      :security-groups ["www" "allow-DB"]
                      :username "ubuntu"
                      :public-key (slurp "www.id_rsa.pub")
                      :private-key (slurp "www.id_rsa")}
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
            :configure (fn [session]
                         (-> session
                             (package/package-source "ubuntu-archive"
                                                     ;; by default, EC2
                                                     ;; ubuntu images only
                                                     ;; use ubuntu mirrors
                                                     ;; hosted on EC2,
                                                     ;; which sometimes go
                                                     ;; down. Add this as
                                                     ;; another mirror for
                                                     ;; reliability
                                                     :aptitude {:url "http://us.archive.ubuntu.com/ubuntu/"
                                                                :scopes ["main" "natty-updates" "universe" "multiverse"]}) ;; TODO the natty is specific to 11.04, change later.
                             (package/packages :aptitude ["nginx" "htop" "mongodb"])
                             (java/java :sun :jdk)
                             (git/git)
                             (nginx/site "circle"
                                         :listen 80
                                         :server_name "circle"
                                         :locations [{:location "/"
                                                      :proxy_pass "http://localhost:8080"
                                                      :proxy_headers {"X-Real-IP" "\\$remote_addr"
                                                                      "X-Forwarded-For" "\\$proxy_add_x_forwarded_for"
                                                                      "Host" "\\$http_host"}}])
                             (nginx/site "default" :action :disable)
                             (service/service "nginx" :action :enable)                             
                             ;;users
                             (thread-expr/let->
                              [username (-> session :user :username)
                               home (str "/home/" (-> session :user :username))]
                              (directory/directory  (str home "/.ssh/")
                                                    :create :action
                                                    :owner username
                                                    :group  username
                                                    :mode "600"
                                                    :path true)
                              (ssh-key/install-key username
                                                   "id_rsa"
                                                   (slurp "www.id_rsa")
                                                   (slurp "www.id_rsa.pub"))
                              (lein/lein)
                              (remote-file/remote-file (str home "/.ssh/config") :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                                       :owner username
                                                       :group username
                                                       :mode "600")
                              (directory/directory (str home "/.pallet/")
                                                   :create :action
                                                   :path true)
                              (remote-file/remote-file (str home "/.pallet/config.clj") :local-file "src/circle/pallet_config.clj" :no-versioning true))))}))

(defn start
  "start a new circle instance"
  []
  (nodes/converge {circle-group 1}))

(defn stop
  "start a new circle instance"
  []
  (nodes/converge {circle-group 0}))