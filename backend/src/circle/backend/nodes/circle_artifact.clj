(ns circle.backend.nodes.circle-artifact
  (:require
   pallet.core
   pallet.phase
   [pallet.action.user :as user]
   [pallet.action.directory :as directory]
   [pallet.action.package :as package]
   [pallet.crate.java :as java]
   [pallet.action.remote-file :as remote-file]
   [pallet.action.service :as service]
   [pallet.crate.automated-admin-user :as automated-admin-user]
   [pallet.crate.git :as git]
   [pallet.crate.ssh-key :as ssh-key]
   [pallet.thread-expr :as thread-expr]))

(def circle-artifact-group
  (pallet.core/group-spec
   "circle"
   :circle-node-spec {:ami "ami-e913dd80"
                      :availability-zone "us-east-1a"
                      :instance-type "t1.micro"
                      :keypair-name "www"
                      :security-groups ["artifacts-server"]
                      :username "ubuntu"
                      :public-key (slurp "www.id_rsa.pub")
                      :private-key (slurp "www.id_rsa")}))