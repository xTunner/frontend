(ns circle.backend.nodes.circle
  (:require pallet.core
            pallet.phase
            [pallet.action.directory :as directory]
            [pallet.action.exec-script :as exec-script]
            [pallet.action.package :as package]
            [pallet.action.remote-file :as remote-file]
            [pallet.action.service :as service]
            [pallet.action.user :as user]
            [pallet.crate.automated-admin-user :as automated-admin-user]
            [pallet.crate.git :as git]
            [pallet.crate.java :as java]
            [pallet.crate.lein :as lein]
            [pallet.crate.network-service :as network-service]
            [pallet.crate.nginx :as nginx]
            [pallet.crate.postgres :as postgres]
            [pallet.crate.rvm :as rvm]
            [pallet.crate.ssh-key :as ssh-key]
            [pallet.crate.rubygems :as rubygems]
            [pallet.stevedore :as stevedore]
            [pallet.thread-expr :as thread-expr]
            [circle.sh :as sh]
            [circle.backend.ssh :as ssh]
            [circle.backend.nodes :as nodes])
  (:use [arohner.utils :only (inspect)]))

;; this is our "memoized" circle box
(def circle-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-efd61d86"
                      :name "www"
                      :availability-zone "us-east-1a"
                      :instance-type "m1.small"
                      :keypair-name "www"
                      :security-groups ["www" "allow-DB"]
                      :username "ubuntu"
                      :public-key (slurp "www.id_rsa.pub")
                      :private-key (slurp "www.id_rsa")}))

(defmacro user-code
  "Runs a seq of stevedore commands as the non-sudo user"
  [session & cmds]
  `(let [cmds# (stevedore/with-script-language :pallet.stevedore.bash/bash
                (clojure.string/join ";" (map (fn [c#]
                                                (stevedore/emit-script [c#])) (quote ~cmds))))]
    (-> ~session
        (exec-script/exec-checked-script
         "rvm cmd"
         ("sudo" "-i" "-u" (unquote (-> ~session :user :username)) "bash" "-c" (unquote (format "\"%s\"" cmds#)))))))

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
                             (package/packages :aptitude ["nginx" "htop" "mongodb" "rubygems" "libsqlite3-dev" "nodejs"])
                             (java/java :openjdk :jdk)
                             (git/git)
                             (exec-script/exec-script
                              ~(stevedore/checked-script
                                "Update rubygems"
                                (sudo "REALLY_GEM_UPDATE_SYSTEM=true" gem update --system)))

                             (rvm/rvm)
                             (user-code
                              (source "~/.bash_profile") ;; make sure RVM is loaded
                              (rvm install jruby)
                              (rvm use jruby)
                              (rvm gemset create circle)
                              (rvm gemset use circle)
                              (gem install bundler)
                              (gem install rspec))

                             (nginx/site "circle"
                                         :listen 80
                                         :server_name "circle"
                                         :locations [{:location "/"
                                                      :proxy_pass "http://localhost:3000"
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