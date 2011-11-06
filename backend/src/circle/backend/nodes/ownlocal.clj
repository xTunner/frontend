(ns circle.backend.nodes.ownlocal
  (:require
   pallet.core
   pallet.phase
   [pallet.action.user :as user]
   [pallet.action.directory :as directory]
   [pallet.action.package :as package]
   [pallet.action.remote-file :as remote-file]
   [pallet.action.service :as service]
   [pallet.crate.automated-admin-user :as automated-admin-user]
   [pallet.crate.git :as git]
   [pallet.crate.ssh-key :as ssh-key]
   [pallet.thread-expr :as thread-expr]))

(def ownlocal-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-bbf539d2" ;; clean 11.04 x64 server
                      :availability-zone "us-east-1a"
                      :instance-type "t1.micro"
                      :keypair-name "www"
                      :security-groups ["www"]
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
                                                                :scopes ["main" "universe" "multiverse"]})
                             (package/packages :aptitude ["ruby1.9.1" "ruby1.9.1-dev" "mysql-server" "build-essential" "libxml2" "libxml2-dev" "libxslt1-dev" "libmysql++-dev" "imagemagick" "poppler-utils" "libgraphicsmagick++-dev" "libmagickwand-dev"])
                             (git/git)
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
                              (remote-file/remote-file (str home "/.ssh/config") :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                                       :owner username
                                                       :group username
                                                       :mode "600")
                              ;;sudo REALLY_GEM_UPDATE_SYSTEM=true gem update --system
                              ;;gem install bundler
                              )))}))