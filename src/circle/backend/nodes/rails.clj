(ns circle.backend.nodes.rails
  (:require [clojure.java.classpath :as cp])
  (:use [circle.util.except :only (eat)])
  (:require pallet.core
            pallet.phase
            [pallet.action.directory :as directory]
            [pallet.action.exec-script :as exec-script]
            [pallet.action.file :as file]
            [pallet.action.package :as package]
            [pallet.action.remote-file :as remote-file]
            [pallet.crate.automated-admin-user :as automated-admin-user]
            [pallet.crate.git :as git]
            [pallet.crate.rvm :as rvm]
            [pallet.crate.java :as java]
            [pallet.crate.postgres :as postgres]
            [pallet.crate.ssh-key :as ssh-key]
            [pallet.crate.node-js :as node-js]
            [pallet.stevedore :as stevedore]
            [pallet.thread-expr :as thread-expr]
            [circle.backend.pallet :as circle-pallet])
  (:use [clojure.contrib.with-ns :only (with-ns)])
  (:use [pallet.thread-expr :only (when-> let->)]))

;; pallet 0.6.5 calls binding on vars it doesn't own, which doesn't work in 1.3. These lines can go away when pallet gets an offical clj-1.3 release
(.setDynamic (var cp/classpath))
(.setDynamic (var cp/classpath-jarfiles))

;; default node for all rails projects
(def rails-node
  {:ami "ami-f7d1029e"
   :instance-type "m1.small"
   :username "ubuntu"
   :security-groups ["www"]
   :availability-zone "us-east-1a"})

(defn build-ruby-package []
  ;;checkinstall -D --pkgname ruby-1.8.7 --install=no --fstrans=no rvm install 1.8.7
  ;; requires RVM to be installed as root
  )

(defn install-ruby
  "Install a ruby on the box using RVM, plus some standard gems that every ruby will want. version is a string that RVM understands"
  [session version & {:keys [url]}]

  (-> session
      (when-> url
              (exec-script/exec-script
               (wget -q -P "~/.rvm/archives" ~url)))
      (circle-pallet/user-code
       (source "\"$HOME/.rvm/scripts/rvm\"")
       (rvm install ~version)
       (rvm use ~version)
       (gem install bundler))))

(defn install-all-ruby [session]
  (-> session
      (rvm/rvm :stable true)
      (circle-pallet/install-rvmrc {:rvm_gemset_create_on_use_flag 1
                                    :rvm_install_on_use_flag 1
                                    :rvm_trust_rvmrcs_flag 1})
      (circle-pallet/install-gemrc "gem: --no-ri --no-rdoc")

      (install-ruby "1.8.7")
      (install-ruby "1.9.2")
      (install-ruby "1.9.2-p180")
      (install-ruby "1.9.3-p0-falcon")
      (install-ruby "1.9.3")
      (install-ruby "jruby-1.6.5")
      (install-ruby "jruby-1.6.6")
      (install-ruby "ree")
      (install-ruby "ree-1.8.7-2010.02" :url "http://rubyforge.org/frs/download.php/71096/ruby-enterprise-1.8.7-2010.02.tar.gz")
      (install-ruby "ree-1.8.7-2011.03")
      (install-ruby "ree-1.8.7-head")))

(def ruby-group
  (pallet.core/group-spec
   "ruby"
   :phases {:configure (pallet.phase/phase-fn
                        ;; Ruby
                        (exec-script/exec-script
                         ~(stevedore/checked-script
                           "Update rubygems"
                           (sudo "REALLY_GEM_UPDATE_SYSTEM=true" gem update --system)))

                        (install-all-ruby)
                        (circle-pallet/user-code
                         (source "\"$HOME/.rvm/scripts/rvm\"")
                         (rvm use "1.9.2" --default)
                         (echo $?)))}))

;; by default, the postgres crate includes some permissions we don't
;; want, and merges the defaults in. Bindings are finicky here,
;; because apparently some pallet stuff happens in other threads.
(with-ns 'pallet.crate.postgres
  (def pallet.crate.postgres/default-settings-map (assoc-in pallet.crate.postgres/default-settings-map [:permissions] [])))

(def postgres-group
  (pallet.core/group-spec
   "postgres"
   :phases {:configure
            (pallet.phase/phase-fn
             (package/package-source "martin-pitt-postgres" :aptitude
                                     {:url "ppa:pitti/postgresql"
                                      :release "natty"
                                      :scopes ["main"]})
             (package/package-manager :update)
             (package/packages :aptitude ["postgresql-9.1"])
             (postgres/settings
              (postgres/settings-map
               {:version "9.1"
                :package-source :martin-pitt-backports
                :permissions [{:connection-type "local" :database "all" :user "all" :auth-method "trust"}
                              {:connection-type "host" :database "all" :user "all" :ip-mask "127.0.0.1/32" :auth-method "trust"}
                              {:connection-type "host" :database "all" :user "all" :ip-mask "::1/128" :auth-method "trust"}]}))

             (postgres/initdb)
             (postgres/hba-conf)
             (postgres/service :action :restart)
             (exec-script/exec-checked-script
              "postgres permissions"
              (psql -U postgres -c "'create role ubuntu with superuser login'")
              (psql -U postgres -c "'create role root with superuser login'")
              (createdb ubuntu)))}))

(def mysql-group
  (pallet.core/group-spec
   "mysql"
   :phases {:configure
            (pallet.phase/phase-fn
             ;; MySQL 5.5 changes commented out, gems don't build against it yet.
             ;; (package/package-source "Nathan Rennie-Waldock" :aptitude
             ;;                         {:url "ppa:nathan-renniewaldock"
             ;;                          :release "natty"
             ;;                          :scopes ["main"]})
             (package/package-manager :update)
             (package/packages :aptitude ["mysql-server" "libmysql++-dev"]) ;;["mysql-client-5.5" "mysql-server-5.5" "libmysql++-dev"]
             (exec-script/exec-checked-script
              "mysql permissions"
              (echo "\"CREATE USER 'ubuntu'@'localhost'\"" "|" mysql -u root)
              (echo "\"GRANT ALL PRIVILEGES ON *.* TO 'ubuntu'@'localhost'\"" "|" mysql -u root)
              (echo "\"CREATE USER 'circle'@'localhost'\"" "|" mysql -u root)
              (echo "\"GRANT ALL PRIVILEGES ON *.* TO 'circle'@'localhost'\"" "|" mysql -u root)))}))

(def redis-group
  (pallet.core/group-spec
   "redis"
   :phases {:configure
            (pallet.phase/phase-fn
             (package/package-source "dotdeb" :aptitude
                                     {:url "http://packages.dotdeb.org"
                                      :source-type "deb-src"
                                      :scopes ["all"]
                                      :key-url "http://www.dotdeb.org/dotdeb.gpg"
                                      :release "stable"})
             (package/package-manager :update)
             (package/packages :aptitude ["apt-src"])
             (exec-script/exec-script
              "install redis"
              (sudo apt-src -b --no-p install redis-server)
              (dpkg -i redis-server*)))}))

(def mongo-group
  (pallet.core/group-spec
   "mongo"
   :phases {:configure
            (pallet.phase/phase-fn
             (->
              (package/package-source "mongodb" :aptitude
                                      {:url "http://downloads-distro.mongodb.org/repo/ubuntu-upstart"
                                       :scopes ["10gen"]
                                       :release "dist"})
              (exec-script/exec-script
               "mongodb repo key"
               ;; package-source hardcodes the keyserver, which won't
               ;; work for this key. Add the key manually, but that
               ;; happens after the packages phase, so do apt-get
               ;; install manually as well. This can all be fixed by
               ;; patching pallet.action.package/package-source to
               ;; take a keyserver and key-id at the same time.
               (sudo apt-key adv --keyserver keyserver.ubuntu.com --recv "7F0CEB10")
               (sudo apt-get update)
               (sudo apt-get install mongodb-10gen))))}))

(def riak-group
  (pallet.core/group-spec
   "riak"
   :phases {:configure
            (pallet.phase/phase-fn
             (let-> [filename "riak_1.0.2-1_i386.deb"]
               (remote-file/remote-file filename :url (format "http://downloads.basho.com/riak/riak-1.0.2/%s" filename))
               (exec-script/exec-checked-script
                "install riak"
                (sudo dpkg -i ~filename))))}))

(defn lein
  "install lein script to the specified directory. If not specified, defaults to /usr/local/bin.

   Version is the version of lein to install. Can be any branch or tagname in github.com/techomancy/leiningen. Defaults to 'stable'"
  ([session & {:keys [version
                      install-path]
               :or {install-path "/usr/local/bin/lein"}}]
     (let [lein-url (format "https://raw.github.com/technomancy/leiningen/%s/bin/lein" (or version "stable"))]
       (-> session
           (remote-file/remote-file
            install-path
            :url lein-url
            :insecure true
            :no-versioning true
            :mode "755")
           (circle-pallet/user-code
            (lein self-install))))))

(def clojure-group
  (pallet.core/group-spec
   "clojure"
   :phases {:configure
            (pallet.phase/phase-fn
             (lein))}))

(defn install-everything [session]
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
      (package/packages :aptitude
                        [
                         ;; Administration
                         "htop"
                         "emacs"
                         "links"
                         "vim"

                         ;; build
                         "build-essential"
                         "devscripts"

                         ;; DBs
                         "sphinxsearch"
                         "libpq-dev"
                         "memcached"

                         ;; Ruby - see pallet-rvm for others
                         "rubygems"
                         "libxml2"
                         "libxml2-dev"
                         "libxslt1-dev"

                         "imagemagick"
                         "poppler-utils"
                         "libgraphicsmagick++-dev"
                         "libmagickwand-dev"
                         "libzmq-dev"

                         ;; Other platforms
                         "clojure"
                         "php5-cli"
                         "php-pear"

                         ;; Selenium
                         "firefox"
                         "xvfb"

                         ;; node.js
                         "libssl-dev"

                         ;; Libraries that users need
                         "libcurl4-openssl-dev"
                         "libaspell-dev"
                         "libgeoip-dev"
                         "libmemcached-dev"
                         "libsasl2-dev"
                         "libqtwebkit-dev"
                         "x11-utils"
                         "x11-apps"
                         "zookeeper"

                         "expect-dev"
                         "lftp"
                         "wkhtmltopdf"])


      ;; System setup

      (remote-file/remote-file "/etc/apt/apt.conf" :local-file "pallet/apt.conf" :mode "644" :no-versioning true)
      (remote-file/remote-file "/etc/rc.local" :local-file "pallet/rc.local" :mode "755" :no-versioning true)
      (remote-file/remote-file "/home/ubuntu/.bashrc" :local-file "pallet/bashrc" :mode "644" :no-versioning true)
      (remote-file/remote-file "/home/ubuntu/.bash_profile" :local-file "pallet/bash_profile" :mode "644" :no-versioning true)

      ;; Version control
      (git/git)

      ;; redis doesn't have permissions, AFAICT
      ;; TODO: riak
      ;; TODO: solr
      ;; TODO: memcached
      ;; TODO: couchdb


      ;; Languages and language-specific package managers


      ;; Java
      (java/java :openjdk :jdk)

      ;; php / Pear
      (exec-script/exec-script
        ~(stevedore/checked-script
           "Initialize Pear."
           (sudo pear config-set auto_discover 1)))
      (exec-script/exec-script
        ~(stevedore/checked-script
           "Install PHPUnit."
           (sudo pear install pear.phpunit.de/PHPUnit)))

      ;; NodeJS
      ;; This installs from scratch - alternative available at
      ;; https://github.com/joyent/node/wiki/Installing-Node.js-via-package-manager
      (node-js/install :version "0.6.6")

      ;; users
      (thread-expr/let->
       [username (-> session :user :username)
        home (str "/home/" (-> session :user :username))]
       (directory/directory  (str home "/.ssh/")
                             :create :action
                             :owner username
                             :group  username
                             :mode "600"
                             :path true)
       (ssh-key/authorize-key username
                              (eat (slurp "secret/www.id_rsa.pub")))
       (remote-file/remote-file (str home "/.ssh/config") :content "Host github.com\n\tStrictHostKeyChecking no\n"
                                :owner username
                                :group username
                                :mode "600")
       (directory/directory (str home "/.pallet/")
                            :create :action
                            :path true))))

(def rails-group
  (pallet.core/group-spec
   "rails"
   :circle-node-spec {:ami "ami-06ad526f" ;; clean ubuntu 11.10
                      :availability-zone "us-east-1a"
                      :instance-type "c1.medium"
                      :keypair-name "www"
                      :security-groups ["www"]
                      :username "ubuntu"
                      :public-key (eat (slurp "secret/www.id_rsa.pub"))
                      :private-key (eat (slurp "secret/www.id_rsa"))}
   :extends [postgres-group redis-group mongo-group ruby-group riak-group mysql-group clojure-group]
   :phases {:bootstrap (pallet.phase/phase-fn
                        (automated-admin-user/automated-admin-user))
            :configure  (pallet.phase/phase-fn
                         (install-everything))}))
