(ns circle.backend.nodes
  (:require pallet.core
            pallet.compute
            pallet.phase
            [pallet.crate.automated-admin-user :as automated-admin-user]
            [pallet.crate.git :as git]
            [pallet.crate.rubygems :as rubygems]
            [pallet.crate.rvm :as rvm]
            pallet.resource.package
            pallet.action.package
            [pallet.resource :as resource]
            [pallet.compute.node-list :as node-list]
            [clj-ssh.ssh :as ssh]
            [circle.backend.ec2 :as ec2])
  (:use [pallet.configure :only (defpallet)]
        [circle.util.core :only (apply-map)]))

(def admin-user (pallet.utils/make-user "ubuntu"
                                        :public-key-path "www.id_rsa.pub"
                                        :private-key-path "www.id_rsa"))

(def rails-spec
  ;; configuration needed to build and test Rails the library
  (let [gem (fn [session name]
              (rubygems/gem session name :no-ri true :no-rdoc true))]
    (pallet.core/server-spec
     :phases {:configure (pallet.phase/phase-fn
                          (pallet.action.package/packages :aptitude [ ;;; packages to build new versions of ruby
                                                                     
                                                                     ;; packages to build rails
                                                                     "libxml2" "libxml2-dev" "libxslt1-dev" "sqlite3" "libsqlite3-dev" "mysql-server" "libmysqlclient15-dev" "postgresql" "postgresql-client" "postgresql-contrib" "libpq-dev"])
                          (rubygems/rubygems "1.3.7")
                          (rvm/rvm)
                          (gem "rake")
                          (gem "bundler")
                          (gem "ci_reporter"))})))

;;; TODO setup steps
;; bundle install --without db

(def builder-group
  (pallet.core/group-spec
   "Linux image for building"
   :node-spec (pallet.core/node-spec
               :image {:os-family :ubuntu
                       :location-id "us-east-1"
                       :image-id "us-east-1/ami-06ad526f"}
               :network {:inbound-ports [22]})
   :extends rails-spec
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
            :configure (pallet.phase/phase-fn
                        (pallet.action.package/package "git"))}))

(defn pallet-compute-service
  "Returns a pallet compute service, so we can call pallet lift on arbitrary boxes (i.e. boxes not started by pallet)."
  [group-spec ip-addrs & {:keys [os]
                          :or {os (keyword "ubuntu")}}]
  (let [conn-map
        {:provider "node-list"
         :node-list (map #(node-list/make-node "www" (name (:group-name group-spec)) % os) ip-addrs)
         :environment {:user admin-user}}]
    (pallet.compute/compute-service-from-map conn-map)))

(defn configure-instance-ids
  [instance-ids group-spec]
  (pallet.core/lift group-spec
                    :phase :configure
                    :compute (pallet-compute-service group-spec (map ec2/public-ip instance-ids))))