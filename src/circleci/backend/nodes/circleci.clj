(ns circleci.backend.nodes.circleci
  (:require pallet.core
            pallet.phase
            [pallet.action.user :as user]
            [pallet.action.directory :as directory]
            [pallet.action.package :as package]
            [pallet.action.remote-file :as remote-file]
            [pallet.crate.automated-admin-user :as automated-admin-user]
            [pallet.crate.git :as git]
            [pallet.crate.lein :as lein]
            [pallet.crate.java :as java]
            [pallet.crate.ssh-key :as ssh-key]
            [pallet.crate.network-service :as network-service]
            [pallet.crate.postgres :as postgres]
            [circleci.backend.nodes :as nodes]))

;; The node configuration to build the circleci box

(def circleci-group
  (pallet.core/group-spec
   "CircleCI box"
   :node-spec (pallet.core/node-spec
               :hardware {:hardware-id "m1.small"} ;; require m1.small or larger right now, because of https://bugs.launchpad.net/ubuntu/+source/linux-ec2/+bug/634487
               :image {:os-family :ubuntu
                       :location-id "us-east-1"
                       :image-id "us-east-1/ami-06ad526f"}
               :network {:inbound-ports [22 80 8080]})
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
            :configure (pallet.phase/phase-fn
                        (java/java :openjdk)
                        (git/git)
                        (postgres/settings (postgres/settings-map {:version "8.4"
                                                                   :permissions [{:connection-type "local" :database "all" :user "all" :auth-method "trust"}
                                                                                 {:connection-type "host" :database "all" :user "all" :ip-mask "127.0.0.1/32" :auth-method "trust"}
                                                                                 {:connection-type "host" :database "all" :user "all" :ip-mask "::1/128" :auth-method "trust"}]}))
                        
                        (postgres/postgres)
                        (postgres/initdb)
                        (postgres/hba-conf)
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
                        (lein/lein)
                        (remote-file/remote-file "/home/circle/.ssh/config" :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                                 :owner "circle"
                                                 :group "circle"
                                                 :mode "644"))}))

(defn start
  "start a new circleCI instance"
  []
  (nodes/converge {circleci-group 1}))

(defn stop
  "start a new circleCI instance"
  []
  (nodes/converge {circleci-group 0}))