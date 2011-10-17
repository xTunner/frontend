(ns circle.backend.nodes.circle-db
  (:require pallet.core
            pallet.phase
            [pallet.action.directory :as directory]
            [pallet.action.exec-script :as exec-script]
            [pallet.action.package :as package]
            [pallet.action.remote-file :as remote-file]
            [pallet.action.service :as service]
            [pallet.crate.automated-admin-user :as automated-admin-user]
            [pallet.crate.network-service :as network-service]
            [pallet.crate.postgres :as postgres]
            [pallet.crate.ssh-key :as ssh-key]
            [pallet.action.user :as user]
            [pallet.parameter :as parameter]
            [circle.backend.nodes :as nodes]
            [circle.admin.ebs :as ebs]))

;; the node spec for our DB server

(def DB-snapshot-name "circle-DB")

(def pg-data-dir "/data/db")


(def circle-db
  (pallet.core/group-spec
   "circleDB"
   :node-spec (pallet.core/node-spec
               :hardware {:hardware-id "t1.micro"
                          ;;:map-new-volume-to-device-name ["/dev/sdf" 1 true]
                          :map-ebs-snapshot-to-device-name ["/dev/sdf" (ebs/latest-snapshot DB-snapshot-name) 1 false]} 
               :image {:os-family :ubuntu
                       :os-version-matches "11.04"
                       :location-id "us-east-1"}
               :network {:security-groups ["DB"]})
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
            :settings (pallet.phase/phase-fn
                       (postgres/settings (postgres/settings-map {:options {:data_directory "/data/db/%s"
                                                                            :listen_addresses "*"
                                                                            :ssl "on"}
                                                                  :permissions [{:connection-type "local" :database "all" :user "all" :auth-method "trust"}
                                                                                {:connection-type "host" :database "all" :user "all" :ip-mask "127.0.0.1/32" :auth-method "trust"}
                                                                                {:connection-type "host" :database "all" :user "all" :ip-mask "::1/128" :auth-method "trust"}
                                                                                {:connection-type "hostssl" :database "all" :user "circle" :ip-mask "0.0.0.0/0" :auth-method "md5"}]}))
                       (parameter/update-target-settings :postgresql nil update-in [:clusters :main :permissions] #(drop 2 %)) ;; currently no easy way to remove the first two default auth settings, so remove them manually.
                       )
            :configure (pallet.phase/phase-fn
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
                        
                        ;; (exec-script/exec-script (mkfs "-t" ext4 "/dev/xvdf"))
                        (directory/directory "/data/" :action :create :path true)
                        (exec-script/exec-script "ls /dev/xvd*")
                        (exec-script/exec-script (mount "/dev/xvdf" "/data"))
                        (directory/directory "/data/db" :action :create :path true)

                        ;; packages get installed early in the
                        ;; process, regardless of the location of
                        ;; (postgres). On ubuntu, services are started
                        ;; at install time. We're going to change the
                        ;; hba.conf, and the data directory, both
                        ;; require restarts. postgres gets pissy if
                        ;; you change the directory and then attempt
                        ;; to restart. So just stop it now.
                        
                        (postgres/service :action :stop)
                        (postgres/postgresql-conf)
                        (postgres/hba-conf)
                        (postgres/service-config)
                        (postgres/postgres)
                        (remote-file/remote-file "/data/db/main/server.key" :local-file "db-ssl.key"
                                                 :owner "postgres"
                                                 :group "postgres"
                                                 :mode "600")
                        (remote-file/remote-file "/data/db/main/server.crt" :local-file "db-ssl.crt"
                                                 :owner "postgres"
                                                 :group "postgres"
                                                 :mode "600")
                        ;; (postgres/initdb)
                        (postgres/service :action :start)
                        (pallet.crate.network-service/wait-for-port-listen 5432)
                                                
                        (postgres/create-database "circleci")
                        (postgres/create-role "circle" :user-parameters [:login :encrypted :password (format "'%s'" (-> circle.db/db-map :password))])
                        (postgres/postgresql-script :db-name "circleci"
                                                    :content "GRANT ALL on ALL TABLES IN SCHEMA public to circle;
                                                              ALTER DEFAULT PRIVILEGES FOR circle IN SCHEMA public GRANT ALL ON TABLES TO circle"
                                                    :literal true)
                        (user/user "circle"
                                   :action :create
                                   :shell :bash
                                   :create-home true
                                   :groups #{"circle"})
                        (directory/directory "/home/circle/.ssh/"
                                             :create :action
                                             :path true
                                             :owner "circle"
                                             :group "circle")
                        (ssh-key/install-key "circle"
                                             "id_rsa"
                                             (slurp "www.id_rsa")
                                             (slurp "www.id_rsa.pub"))
                        (ssh-key/authorize-key "circle"
                                               (slurp "www.id_rsa.pub"))
                        (remote-file/remote-file "/home/circle/.ssh/config" :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                                 :owner "circle"
                                                 :group "circle"
                                                 :mode "644"))}))