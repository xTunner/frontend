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
                          ;; :map-new-volume-to-device-name ["/dev/sdf" 1 true]
                          :map-ebs-snapshot-to-device-name ["/dev/sdf" (ebs/latest-snapshot "DB backup") 1 true]} 
               :image {:os-family :ubuntu
                       :os-version-matches "11.04"
                       :location-id "us-east-1"}
               :network {:inbound-ports [22]})
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
            :settings (pallet.phase/phase-fn
                       (postgres/settings (postgres/settings-map {:version "8.4"
                                                                  :options {:data_directory "/data/db/%s"}
                                                                  :permissions [{:connection-type "local" :database "all" :user "all" :auth-method "trust"}
                                                                                {:connection-type "host" :database "all" :user "all" :ip-mask "127.0.0.1/32" :auth-method "trust"}
                                                                                {:connection-type "host" :database "all" :user "all" :ip-mask "::1/128" :auth-method "trust"}]}))
                       (parameter/update-target-settings :postgresql nil update-in [:clusters :main :permissions] #(drop 2 %)) ;; currently no easy way to remove the first two default auth settings, so remove them manually.
                       )
            :configure (pallet.phase/phase-fn
                        ((fn [session]
                           (println "*** Postgresql settings:" (parameter/get-target-settings session :postgresql nil))
                          session))
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
                        (postgres/postgresql-conf)
                        (postgres/hba-conf)
                        (postgres/postgres)
                        (postgres/initdb)
                        (exec-script/exec-script (pkill postgres))
                        (postgres/service :action :restart)
                        (postgres/create-database "circleci")
                        ;;users
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
                        (pallet.crate.network-service/wait-for-port-listen 5432)
                        (remote-file/remote-file "/home/circle/.ssh/config" :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                                 :owner "circle"
                                                 :group "circle"
                                                 :mode "644"))}))