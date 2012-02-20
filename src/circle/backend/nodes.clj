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
            [circle.backend.ec2 :as ec2]
            [circle.backend.nodes.rails :as rails]
            [circle.backend.build.config])
  (:use [pallet.configure :only (defpallet)]
        [circle.util.core :only (apply-map)]))

(def admin-user (pallet.utils/make-user "ubuntu"
                                        :public-key-path "secret/www.id_rsa.pub"
                                        :private-key-path "secret/www.id_rsa"))


(defn pallet-compute-service
  "Returns a pallet compute service, so we can call pallet lift on arbitrary boxes (i.e. boxes not started by pallet)."
  [group-spec ip-addrs & {:keys [os]
                          :or {os (keyword "ubuntu")}}]
  (let [conn-map
        {:provider "node-list"
         :node-list (map #(node-list/make-node "www" (name (:group-name group-spec)) % os) ip-addrs)
         :environment {:user admin-user}}]
    (pallet.compute/compute-service-from-map conn-map)))

;; TECHNICAL_DEBT use robert hooke here.
(defn session-hook
  "called after a new session is created"
  [session]
  (.setServerAliveCountMax session 30))

(defn connect-hook
  "called after a new session is connected"
  [session]
  ;; connect may also be called w/ sftp connections, which we don't want to hook
  (cond
   (not (instance? com.jcraft.jsch.Session session)) nil
   (not (ssh/connected? session)) nil
   :else (.setServerAliveInterval session (* 60 1000))))

(defonce old-ssh-session clj-ssh.ssh/session)
(defonce old-ssh-connect clj-ssh.ssh/connect)

(defn set-custom-ssh! []
  (println "setting custom clj-ssh/session")
  (alter-var-root (var clj-ssh.ssh/session) (constantly (fn [& args]
                                                          (let [s (apply old-ssh-session args)]
                                                            (session-hook s)
                                                            s))))

  (alter-var-root (var clj-ssh.ssh/connect) (constantly (fn [session]
                                                          (let [result (old-ssh-connect session)]
                                                            (connect-hook session)
                                                            result)))))

(defn undo-custom-ssh []
  (alter-var-root (var clj-ssh.ssh/session) (constantly old-ssh-session))
  (alter-var-root (var clj-ssh.ssh/connect) (constantly old-ssh-connect)))

(defn configure [ip-addrs group-spec]
  (set-custom-ssh!)
  (pallet.core/lift group-spec
                    :phase :configure
                    :compute (pallet-compute-service group-spec ip-addrs)))

(defn configure-ec2
  "Runs pallet configure on a seq of ec2 instances"
  [instance-ids group-spec]
  (configure (map ec2/public-ip instance-ids) group-spec))

(defn start-and-configure [group-spec]
  (let [instance-ids (ec2/start-instances (-> group-spec :circle-node-spec))]
    (configure-ec2 instance-ids group-spec)))

(defn memoize-group-spec
  "Starts an instance, pallet configures it, creates a new image."
  [group-spec image-name]
  (let [instance-ids (ec2/start-instances (-> group-spec :circle-node-spec))]
    (configure-ec2 instance-ids group-spec)
    (ec2/create-image (first instance-ids) image-name)
    (apply ec2/terminate-instances! instance-ids)))

(defn start-instance
  "Start an instance of the default customer image."
  [& {:keys [clean]}]
  (ec2/start-instances (merge (-> circle.backend.nodes.rails/rails-group :circle-node-spec)
                              {:instance-type "m1.small"
                               :ami (-> circle.backend.nodes.rails/rails-node :ami)})))

(defn start-circle-instance []
  (ec2/start-instances (merge (-> (circle.backend.build.config/read-yml-config ".")
                                  :nodes
                                  :www
                                  (update-in [:public-key] slurp)
                                  (update-in [:private-key] slurp))
                              {:keypair-name "www"})))

;; TODO: vmfest could probably do this better
(defn setup-vagrant []
  (let [conn-map {:provider "node-list"
                  :node-list [(node-list/make-node "vagrant" "vagrant" "127.0.0.1" :ubuntu :ssh-port 2222)]
                  :environment {:user (pallet.utils/make-user "vagrant"
                                                       :private-key-path "/Users/pbiggar/.rvm/gems/ruby-1.9.2-p290@global/gems/vagrant-0.8.10/keys/vagrant")}}
        service (pallet.compute/compute-service-from-map conn-map)
        server (pallet.core/server-spec
                :node-spec (pallet.core/node-spec :image {:os-family :ubuntu})
                :phases {:configure (pallet.phase/phase-fn
                                     (rails/install-everything))})
        group (pallet.core/group-spec "vagrant"
                               :extends server)]
    (pallet.core/lift group
               :phase :configure
               :compute service)))