(ns circle.backend.nodes.circle
  (:require pallet.core
            pallet.phase
            [pallet.action.directory :as directory]
            [pallet.action.exec-script :as exec-script]
            [pallet.action.file :as file]
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
            [circle.backend.nodes :as nodes]
            [circle.backend.pallet :as circle-pallet])
  (:require [circle.backend.nodes.rails :as rails])
  (:use [circle.util.except :only (eat)]))

;; this is our "memoized" circle box
(def circle-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-3bb46152"
                      :name "www"
                      :availability-zone "us-east-1a"
                      :instance-type "m1.small"
                      :keypair-name "www"
                      :security-groups ["www" "allow-DB"]
                      :username "ubuntu"
                      :public-key (eat (slurp "secret/www.id_rsa.pub"))
                      :private-key (eat (slurp "secret/www.id_rsa"))}))

;; The configuration to build the circle box from scratch
(def circle-raw-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-6fa27506" ;; clean ubuntu 11.10, amd64
                      :availability-zone "us-east-1a"
                      :instance-type "m1.large"
                      :keypair-name "www"
                      :security-groups ["www" "allow-DB"]
                      :username "ubuntu"
                      :public-key (eat (slurp "secret/www.id_rsa.pub"))
                      :private-key (eat (slurp "secret/www.id_rsa"))}
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
                                                                :scopes ["main" "updates" "universe" "multiverse"]})
                             (package/packages :aptitude ["nginx" "htop" "mongodb" "rubygems" "libsqlite3-dev" "nodejs" "firefox" "xvfb" ])

                             (remote-file/remote-file "/etc/rc.local" :local-file "pallet/rc.local" :mode "755" :no-versioning true)
                             (remote-file/remote-file "/home/ubuntu/.bashrc" :local-file "pallet/bashrc" :mode "644" :no-versioning true)
                             (remote-file/remote-file "/home/ubuntu/.bash_profile" :local-file "pallet/bash_profile" :mode "644" :no-versioning true)

                             (java/java :openjdk :jdk)
                             (git/git)
                             (exec-script/exec-script
                              ~(stevedore/checked-script
                                "Update rubygems"
                                (sudo "REALLY_GEM_UPDATE_SYSTEM=true" gem update --system)))

                             (rvm/rvm)
                             (circle-pallet/user-code
                              (source "~/.bashrc") ;; make sure RVM is loaded
                              (rvm install jruby)
                              (rvm use jruby)
                              (rvm gemset create circle)
                              (rvm gemset use circle)
                              (gem install bundler)
                              (gem install rspec))

                             (directory/directory "/etc/nginx/certs" :action :create)
                             (file/file "/etc/nginx/sites-enabled/default" :action :delete)
                             (remote-file/remote-file "/etc/nginx/sites-enabled/circle" :local-file "nginx-circle.conf" :no-versioning true)
                             (remote-file/remote-file "/etc/nginx/certs/circleci.com.crt" :local-file "secret/circleci.com.crt" :no-versioning true)
                             (remote-file/remote-file "/etc/nginx/certs/circleci.com.key" :local-file "secret/circleci.com.key" :no-versioning true)
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
                                                   (slurp "secret/www.id_rsa")
                                                   (slurp "secret/www.id_rsa.pub"))
                              (remote-file/remote-file (str home "/.ssh/config") :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                                       :owner username
                                                       :group username
                                                       :mode "600")
                              (directory/directory (str home "/.pallet/")
                                                   :create :action
                                                   :path true))))
            :extends [rails/clojure-group]}))