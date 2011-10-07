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
            [circle.backend.nodes :as nodes]))

;; The node configuration to build the circle box

(def circle-group
  (pallet.core/group-spec
   "circle"
   :node-spec (pallet.core/node-spec
               :hardware {:hardware-id "m1.small"} ;; require m1.small or larger right now, because of https://bugs.launchpad.net/ubuntu/+source/linux-ec2/+bug/634487
               :image {:os-family :ubuntu
                       :location-id "us-east-1"
                       :image-id "us-east-1/ami-06ad526f"}
               :network {:inbound-ports [22 80]})
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
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
                        (package/packages :aptitude ["nginx"])
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
  "start a new circle instance"
  []
  (nodes/converge {circle-group 1}))

(defn stop
  "start a new circle instance"
  []
  (nodes/converge {circle-group 0}))