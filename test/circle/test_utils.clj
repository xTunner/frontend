(ns circle.backend.build.test-utils
  (:require [circle.env :as env])
  (:require [somnium.congomongo :as mongo])
  (:require [circle.model.build :as build])
  (:require [circle.model.project :as project])
  (:require circle.backend.build.template)
  (:require circle.init)
  (:require [clj-yaml.core :as yaml])
  (:use [circle.util.except :only (eat throw-if-not)])
  (:use [circle.db :only (test-db-connection)])
  (:require [circle.ruby :as ruby])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.sh :as sh])
  (:use [midje.sweet])
  (:import java.io.StringReader))

;; Test repos
(def repo-prefix "test_data/inference/test_dirs")
(defn test-repo [name] (fs/join repo-prefix name))
(def empty-repo (test-repo "empty"))

;; Initialize them here so we know they're always available.
(circle.init/init)

(defn ensure-project [p]
  (when-not (env/production?)
    (throw-if-not (-> p :vcs_url) "vcs_url is required")
    (mongo/destroy! :projects (select-keys p [:vcs_url]))
    (project/insert! p)))

(defn ensure-user [u]
  (when-not (env/production?)
      (mongo/destroy! :users (select-keys u [:email]))
      (mongo/insert! :users u)))

(defn ensure-user-is-project-member [u p]
  (when-not (env/production?)
    (let [pid (-> p ensure-project :_id)
          uid (-> u ensure-user :_id)
          newp (merge p {:user_ids [uid]})
          newu (merge u {:project_ids [pid]})]
      (mongo/update! :projects p newp)
      (mongo/update! :users u newu))))


(defn minimal-node []
  {:username "ubuntu"
   :public-key "ssh-rsa public key"
   :private-key "ssh-rsa private key"
   :keypair-name "test-keypair"})

(def circle-config
  (->
   "nodes:
  www:
    type: node
    ami: ami-a5c70ecc
    instance-type: \"m1.small\"
    username: ubuntu

    ## following only necessary for deployment
    availability-zone: \"us-east-1a\" # customers *could* specify this, probably shouldn't care.
    public-key: \"secret/www.id_rsa.pub\"
    private-key: \"secret/www.id_rsa\"

jobs:
  build:
    template: build
    node: www   # name of node to use. unique per-project
    commands:
      - lein deps:
          pwd: backend
      - lein midje:
          pwd: backend
          environment:
            CIRCLE_ENV: \"staging\"
    #  on-success: deploy # another build to trigger if this one is successful
    notify_emails:
      # Recognized keys, :committer, :owner, or email literals.
      - :committer
      - :owner

  deploy:
    template: deploy
    commands:
      - lein deps:
          pwd: backend
      - lein daemon start \":web\":
          pwd: backend
          environment:
            CIRCLE_ENV: \"production\"
            SWANK: \"true\"
      - sudo /etc/init.d/nginx start

schedule:
  commit:
    job: build
  nightly:
    job: long-tests

    # branches:
    #   only:
    #   exclude:"
   (StringReader.)
   (yaml/parse-string)))

(def circle-github-json
  ;; These values are not valid for this project, I just filled in enough to work
  {:before "93b6b60abe16792a773993b403622d05b7f3ede1",
   :after "a43319337f12c1926b5e024e2e3cb477ba459c8b",
   :pusher {:email "arohner@gmail.com", :name "arohner"},
   :ref "refs/heads/master",
   :forced false,
   :compare "https://github.com/circleci/test-yml/compare/7ef45f9...9538736",
   :created false,
   :commits [{:message "Rename", :distinct true, :id "0d35a143ced7878d7140a8a1667c8e9f12efb76d", :url "https://github.com/circleci/test-yml/commit/0d35a143ced7878d7140a8a1667c8e9f12efb76d", :timestamp "2011-11-16T12:44:52-08:00", :author {:email "arohner@gmail.com", :username "arohner", :name "Allen Rohner"}, :removed ["circleci.yml"], :modified [], :added ["circle.yml"]}],
   :repository
   {:has_issues true,
    :fork false,
    :pushed_at "2011/11/16 12:44:58 -0800",
    :name "circle",
    :watchers 2,
    :has_wiki true,
    :owner {:email "arohner@gmail.com", :name "arohner"},
    :language "Clojure",
    :size 1368,
    :created_at "2011/09/06 15:51:21 -0700",
    :private true,
    :homepage "",
    :url "https://github.com/circleci/test-yml",
    :has_downloads true,
    :open_issues 0,
    :forks 0,
    :description ""},
   :deleted false})

(def circle-dummy-project-json-str "{\"before\":\"7802df3088473d8d88e0f5481d294608cc6facdd\",\"after\":\"78f58846a049bb6772dcb298163b52c4657c7d45\",\"pusher\":{\"name\":\"none\"},\"ref\":\"refs/heads/master\",\"forced\":false,\"compare\":\"https://github.com/arohner/circle-dummy-project/compare/7802df3...78f5884\",\"created\":false,\"commits\":[{\"timestamp\":\"2011-12-12T22:35:49-08:00\",\"author\":{\"email\":\"arohner@gmail.com\",\"username\":\"arohner\",\"name\":\"Allen Rohner\"},\"removed\":[],\"added\":[\"dummy.id_rsa\"],\"url\":\"https://github.com/arohner/circle-dummy-project/commit/78f58846a049bb6772dcb298163b52c4657c7d45\",\"message\":\"Add SSH keys\",\"modified\":[],\"distinct\":true,\"id\":\"78f58846a049bb6772dcb298163b52c4657c7d45\"}],\"repository\":{\"has_issues\":true,\"fork\":false,\"pushed_at\":\"2011/12/12 22:36:45 -0800\",\"name\":\"circle-dummy-project\",\"watchers\":1,\"has_wiki\":true,\"owner\":{\"email\":\"arohner@gmail.com\",\"name\":\"arohner\"},\"size\":96,\"created_at\":\"2011/12/12 22:32:31 -0800\",\"private\":true,\"homepage\":\"\",\"url\":\"https://github.com/arohner/circle-dummy-project\",\"has_downloads\":true,\"open_issues\":0,\"forks\":0,\"description\":\"\"},\"deleted\":false}")

(def circle-dummy-project-json
  {:before "7802df3088473d8d88e0f5481d294608cc6facdd",
   :after "78f58846a049bb6772dcb298163b52c4657c7d45",
   :pusher {:name "none"},
   :ref "refs/heads/master",
   :forced false,
   :compare "https://github.com/arohner/circle-dummy-project/compare/7802df3...78f5884",
   :created false,
   :commits [{:message "Add SSH keys", :distinct true, :id "78f58846a049bb6772dcb298163b52c4657c7d45", :url "https://github.com/arohner/circle-dummy-project/commit/78f58846a049bb6772dcb298163b52c4657c7d45", :timestamp "2011-12-12T22:35:49-08:00", :author {:email "arohner@gmail.com", :username "arohner", :name "Allen Rohner"}, :removed [], :modified [], :added ["dummy.id_rsa"]}],
   :repository {:has_issues true, :fork false, :pushed_at "2011/12/12 22:36:45 -0800", :name "circle-dummy-project", :watchers 1, :has_wiki true, :owner {:email "arohner@gmail.com", :name "arohner"}, :size 96, :created_at "2011/12/12 22:32:31 -0800", :private true, :homepage "", :url "https://github.com/arohner/circle-dummy-project", :has_downloads true, :open_issues 0, :forks 0, :description ""},
   :deleted false})

(def yml-project
  (project/project
   :name "YML"
   :vcs_type "git"
   :lb-name "www"
   :vcs_url "https://github.com/circleci/test-yml"
   :ssh_private_key "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAuz/HOMYfSz7jGeyqySK5Sqt0qMsHj3KfZGUBCOf3aHGr5bSt\nH3szolgrJo+G6yy8mGHQweDv7ZzgKYKNlN7IIj4ED15DQL+pMsy++eM0EcKUD6Xi\nktmH+OyIOLPvZTdMNpHMIiivMNqsKRwwb8y+kbje+jYpASDCvLKss5Bp1poTKo6M\nSeBpdykSKJf0EL4kT+WSTpn8o9bX7pvW1hp6164L6GOoIoLf4GSr6hDejimEDrM0\nKk/8rDN03gQNjI1b/nOtLoVu6P9s0lNd5r0oMlGHI11q1i3weCAV1qujW6CYEAEx\nyamxcAOM+Xt8dRtXYoGKdK6URo8keFqZfcjEGQIDAQABAoIBAGTtyrd6axHG8uhd\nXe0Ob2ulITt+PDQA55NYsgcX6Y59ftdQ3OQUJ0/BUHj3chS/R2SnjLuEQC8GmPkm\n6qOstZlnbg7Ok1eKN2wvWl0dL0rHY8FPe+IFqLnu3LobmtUmyki7q5lZ7pxZseRy\n0lTgunOXf3DE0rNN8kl90YL0O2g1NYmQBcwsUlGWKsL/AmwC8SjvIJqTFNP4rtzD\n/x/xpi575nuF1nVjV1paqNAP5iA085S9GHdKaqhOOh49NePgNcHJFnMgMxgGZrm6\nHS69DDhuLl+pFJkOAqoN4I6SgTxP3zGaoQnjjrrJ1qJdeZtAJ635bfJLOHLtkwnC\n8ZkvrAECgYEA7SdXCU422ahLUAbTD+byvvgzExiQvhd1U+QJYMkG0VxWgZuL9h/F\n5Cy7o/+YlXjuyRyIQ3cDpftoGagQS4/H1DH2FAJcue+yjAbIulNIKT9E6eP+hyhF\nSBiXg2DDK0IHdThfsdQ47uzF6sQOpbmUGo48he3mfMGJkSI9+5Un2ZkCgYEAyiEt\n2UsWqWIkslUQokBwUH5uKxrKKeEqwjtS2uaRY6JjXhkCfwihqBwlttasVP8IBHCh\nTcrdw6/4EwyP7fRKucCZdD2CxzhaUNq5pMP9C0IysP1PoBXtgDApyMYF7Bx1wKvN\nZb/3xEzXOStYAtqtdXEWIzDF6ZKWhdrCx89TzoECgYEAqrDAAmCbNfndixH+Y9m0\nKiN2j24E7+zuc50T6ueF0raFRO/xwwqHYn2X6KgU6LCMHZA9u2Ez7QmQmbbPU7N+\n/omfNjOlDecqOYLAgutiat2w+i8xiZJAm22nz9WbY804lPQoXFQFTmJ46UqHyFxT\n7vElOrhKRsz9+MoOlr96WTECgYB4uzKwjKo/iDCVGDw0PbsYSTwyoPDJ7QVmOJr3\nxypmUbN6nAOirxwn1pAsUrNinWZDiKbAWYD2hp/teN5rajRMGR9PkAISTrWbf9nM\n8Yeudt7iWpt56j8PBzWeB7G34xPufm/T68LpTGBtdFdAdS+Qa3imklZUektQLmeT\ng2HygQKBgE7B/Fyh1gcdlQVPfCS1Kax6Hu2ereDAfv7ug+pMiNG4SWqh5HCeeoh9\ni56dTngoTJeGZA1s37CzV/2JVyWmBUW4og/NRJWDT5LNQtiJ9rbnKhNFbCdf2WMW\nGuUzdoloFcULJS0hQ1Xgk0KgDF5DoqreRQdf/cGofoL1+l7E6DSl\n-----END RSA PRIVATE KEY-----\n"))

(def test-project
  (project/project
    :name "Dummy Project"
    :vcs_url "https://github.com/arohner/circle-dummy-project"
    :vcs_type "git"
    :lb-name "www"
    :ssh_private_key "-----BEGIN RSA PRIVATE KEY-----\nMIIEpQIBAAKCAQEAqXxicUtAvmBsmE5MolnY1F5O73ZOz49CaSqw+79pmdWln+h4\nNEOHOWpGG8wJZuMo/PhHLjKrdVsK93Pe2CDk6Oq59jqo35A4ts+vQuYC+My7bsrC\n49XgMPtUKzV1F73jr1rJ6oILlpT3S95ZmsNr4CPYPMILzyFr6oYsyrPqlFiKt6so\nqwz2EiIOcwRFMWKZkbYFz4THVTJH2AcSSSuVMTBG8z+mf3gZTnt51joGWxm9ZNGS\nosoFw97qMXaZdqg1QQuIS9dtiz2A/PBT/NR+5NLuLG/v7vKK+IBcilqgjhqhNZSF\nLjoefXcYzPQgeRsbHwcmpMgOJdtdB1g04HCVWQIDAQABAoIBAQCSJhk5etvkjn92\nQqagpPtt/bjxk0JYhz+MMm0VWTS1m97Laook/0oe/35fP+2nlCDnKy0uMDFb7Fsj\nkP5LvunF04ryQhkXV2Ugdyy5cmCDrP+/hQbTsr01X7Rv4hQLmgo5Tv+oweU+P0yU\nVrNL2lIuoJo5olbOkRJe1L7ROJ4CtoPrpEu8kfZbk/sfZ2tCDd7g7IHkRYwe6EAJ\nHc+x25gs1tg9l5z4AQdJF2Vup7tuGHaDfJ/+OC8vF2uF6oWthczlRTpdiDpG0/Vm\nS7/qlrLmUl/rmbKSuY4dwtAM9PDDz0UW5IlZ14Ye6wurR7rQ7/IQ+3A3mdCyjdTi\nPFybhtPRAoGBANT7FlKMflNcc50XCPyWdcJxxbvuF8M5uxuRqArhBmuLiDt7Qo74\nKyAlK8OdVEl8Im1U/HKiyCxFs8b4LXB/eU6zkwcQy38hDrK4Ce/jakeLFlgWRDKu\nIBX/V/pC2FeLNf8l3lCgMxhYGSStl0N+1oDaaY8RCNTRSCzNpgQqJtAdAoGBAMu4\nPaEFJnA9QZXcGDoUf7XGvha3S6WUQ3WiraWQ+y8Eg1ocYWjCz3YPYixmHCMoV6Xl\nLGx0+CBjuXzmu2uZw8nZxI4Qpfai59NBZKOkxDJRU+9JHJX7OBiAml8VDfnXw3J9\n6JYJheKIv5f20END1KLjiXEq7JlSjV2NMx80Uo1tAoGBALPBS8DWRRR0djvColoV\nX0SCo+IOfM3P3uTQ1aSA8ds5A/039iAWwsc5uCs8kVQISkI+tWbHju0W0zn+YtON\nM0RCebg/65DbxL0AaZqnNR82+9/SwzKHFhuazJUzb8bLfwJe4rjnzNgiGu6C+KUF\n22MbdHZEaVC9Zg4yb4kaTnHdAoGAcNsmORezTaaBuNVvDm3gugDZuZfdbmMOZBK1\nA+2nWUq0wazLQc/6QjsPde0zVT66H4sv6v15n+ffEBR6a4eJcT8UpmcOqe1hZCTr\n7cQPdJidWQg1r9i2IMzNuDLfxTMEcV7TBJtN8kszaKowgbMIDxziPTE8VUvoSJU6\nZuUDfpkCgYEA0JU0ANjuxjOHDVIyM+RZXahEnNcNcZ38UazKPNT42MtrUzDveer6\nHMEQ+OH2x3arOzpKy1zezY13wvB1kBx+PTA9Mg5mS7JhmvavG3xvELhVxM+RYpzo\nkotpfuiuM4G/ZuEyRltiePtKMvc4Wturj9AIGbMS6dB86b7HhFdl//I=\n-----END RSA PRIVATE KEY-----\n"))

(def partially-inferred-project
  (project/project
   :name "Dummy Project",
   :vcs_url "https://github.com/arohner/circle-empty-repo",
   :vcs_type "git",
   :test "echo a\necho b"
   :ssh_private_key "-----BEGIN RSA PRIVATE KEY-----\nMIICXQIBAAKBgQCI41xErx6O8WKo6Z3upop9XKhI/gi93Za0vo/LKBc0Wo6b/bEI\nwh37HcRp8ijaNF/D+4wj7OxyBIi70SdARE8JqxOKZwNZx+NrN/ojdNfA7Ggv7JyZ\nSOOGXK+mfzcuCshD4yIqWQmj5zcPjsLwz6AYVk3YCAD4LQFJ0usveK56EwIDAQAB\nAoGAJlcoHMS/1mGdtJnadmGHIJ23NNqSMDvEXlORiuFrvmouz1o7H6zfINqxjMsa\nziMlP4tRMS7G+xhyA8kNKV74k9+X5LMm8DCFgpFvq3Bd/sSYEjXp/W5Kjdi5erL3\njkku1/oNUmfWRzCdnhbL60Q7HpwBT0h/f0D6ZbZLSuu+YuECQQDHZKEKKUS/5F+z\nizWuEY8TzQkN68bWVl2QTR0y8iusyroD2CWFnbcUhfjTG13E5yqM9Bog+CFI+/WG\nIIXWXIRlAkEAr8AIna4FTqcjvjIqhsSlECD/g2w3BmIlkqf/VX+fVmNGeIOVS1nd\nN+5qS0ZBbO1wctQIoUOHgE8YmYng7lZxFwJBAJsCgyOII7dejnvhPJEfe3C0VFar\nNoBI0iItoQaLOCLz05rLfZgbnUnqQR+1Rbee0viTiICbBh1cK2rje8jDUfUCQGec\n0jeLuatGFGd0EehEFIAuxBTJ/qKvyDDvBDR2ugxnGMvB34l94FKyJ05bjATY8ttv\nr+rK8h6uAvW5+LbqlV8CQQCxEAmG93tW22MwoCz4qUrNgcV11uGdZDT5OkZ7MMUw\ntryOfLwDc/BHAx7Yh+rzeY2ivlLThJzChDCH/hE7zTD4\n-----END RSA PRIVATE KEY-----\n"
   :ssh_public_key "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCI41xErx6O8WKo6Z3upop9XKhI/gi93Za0vo/LKBc0Wo6b/bEIwh37HcRp8ijaNF/D+4wj7OxyBIi70SdARE8JqxOKZwNZx+NrN/ojdNfA7Ggv7JyZSOOGXK+mfzcuCshD4yIqWQmj5zcPjsLwz6AYVk3YCAD4LQFJ0usveK56Ew== \n"))

(def test-user
  {:email "user@test.com"})

(defn ensure-test-user []
  (ensure-user test-user))

(defn ensure-test-user-and-project []
  (ensure-project test-project)
  (ensure-test-user)
  (ensure-user-is-project-member test-user test-project))

(def circle-request
  {:remote-addr "127.0.0.1",
   :scheme :http,
   :query-params {},
   :session {},
   :form-params {"payload" "{\"pusher\":{\"name\":\"none\"},\"repository\":{\"name\":\"circle\",\"created_at\":\"2011/09/06 15:51:21 -0700\",\"size\":1420,\"has_wiki\":true,\"watchers\":2,\"private\":true,\"language\":\"Clojure\",\"url\":\"https://github.com/circleci/circle\",\"fork\":false,\"pushed_at\":\"2011/11/18 09:12:29 -0800\",\"has_downloads\":true,\"open_issues\":0,\"homepage\":\"\",\"has_issues\":true,\"description\":\"\",\"forks\":0,\"owner\":{\"name\":\"arohner\",\"email\":\"arohner@gmail.com\"}},\"forced\":false,\"after\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"deleted\":false,\"ref\":\"refs/heads/master\",\"commits\":[{\"added\":[\"backend/src/circle/util/coerce.clj\"],\"modified\":[],\"removed\":[],\"author\":{\"name\":\"Allen Rohner\",\"username\":\"arohner\",\"email\":\"arohner@gmail.com\"},\"timestamp\":\"2011-11-18T09:12:23-08:00\",\"url\":\"https://github.com/circleci/circle/commit/587ae3917fc8e6a9f62e49a276d5572863540410\",\"id\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"distinct\":true,\"message\":\"Add missing file\"}],\"before\":\"a885fe29a55a44c8b25e833b1dedf28e9cf3a1a4\",\"compare\":\"https://github.com/circleci/circle/compare/a885fe2...587ae39\",\"created\":false}"},
   :multipart-params {},
   :request-method :post,
   :query-string nil,
   :content-type "application/x-www-form-urlencoded",
   :cookies {},
   :uri "/github-commit",
   :server-name "www.circleci.com",
   :params {:payload "{\"pusher\":{\"name\":\"none\"},\"repository\":{\"name\":\"circle\",\"created_at\":\"2011/09/06 15:51:21 -0700\",\"size\":1420,\"has_wiki\":true,\"watchers\":2,\"private\":true,\"language\":\"Clojure\",\"url\":\"https://github.com/circleci/circle\",\"fork\":false,\"pushed_at\":\"2011/11/18 09:12:29 -0800\",\"has_downloads\":true,\"open_issues\":0,\"homepage\":\"\",\"has_issues\":true,\"description\":\"\",\"forks\":0,\"owner\":{\"name\":\"arohner\",\"email\":\"arohner@gmail.com\"}},\"forced\":false,\"after\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"deleted\":false,\"ref\":\"refs/heads/master\",\"commits\":[{\"added\":[\"backend/src/circle/util/coerce.clj\"],\"modified\":[],\"removed\":[],\"author\":{\"name\":\"Allen Rohner\",\"username\":\"arohner\",\"email\":\"arohner@gmail.com\"},\"timestamp\":\"2011-11-18T09:12:23-08:00\",\"url\":\"https://github.com/circleci/circle/commit/587ae3917fc8e6a9f62e49a276d5572863540410\",\"id\":\"587ae3917fc8e6a9f62e49a276d5572863540410\",\"distinct\":true,\"message\":\"Add missing file\"}],\"before\":\"a885fe29a55a44c8b25e833b1dedf28e9cf3a1a4\",\"compare\":\"https://github.com/circleci/circle/compare/a885fe2...587ae39\",\"created\":false}"},
   :headers {"x-real-ip" "10.248.202.174", "x-github-event" "push", "accept" "*/*", "host" "www.circleci.com", "x-forwarded-proto" "http", "x-forwarded-for" "207.97.227.253, 10.112.43.159, 10.248.202.174", "content-type" "application/x-www-form-urlencoded", "content-length" "1620", "connection" "close", "x-forwarded-port" "80"},
   :content-length 1620,
   :server-port 80,
   :character-encoding nil,
   ;; :body #<Input org.mortbay.jetty.HttpParser$Input@1579f4e>  real class has this, but it's an unreadable form.
   })

(defn localhost-name []
  (clojure.java.shell/sh "hostname"))

(defn ensure-localhost-ssh []
  "Ensure we're able to SSH into localhost, if this is a CI box."
  (let [home-dir (System/getenv "HOME")]
    (when (and (ec2/self-instance-id)
             (not (fs/exists? (fs/join home-dir ".ssh/id_rsa"))))
    (let [keys (ssh/generate-keys)]
      (spit (fs/join home-dir ".ssh/id_rsa") (-> keys :private-key))
      (spit (fs/join home-dir ".ssh/id_rsa.pub") (-> keys :public-key))
      (sh/sh (format "echo '%s' >> %s" (-> keys :public-key) (fs/join home-dir ".ssh/authorized_keys")))))))

(defn localhost-ssh-map
  "Returns a node-spec that connects to localhost instead of a remote
  box. Can be passed to (minimal-build) in the :node key"
  []
  (ensure-localhost-ssh)
  (let [username (System/getenv "USER")
        ssh-dir (format "%s/.ssh" (System/getProperty "user.home"))]
    (assert username)
    {:username username
     :ip-addr "localhost"
     :public-key (or (eat (slurp (format "%s/id_rsa.pub" ssh-dir)))
                     (eat (slurp (format "%s/id_dsa.pub" ssh-dir))))
     :private-key (or (eat (slurp (format "%s/id_rsa" ssh-dir)))
                      (eat (slurp (format "%s/id_dsa" ssh-dir))))
     :keypair-name "local-keys"}))

(defn minimal-build [& {:keys [_id
                               build_num
                               actions
                               notify_emails
                               node
                               template]
                        :as args}]
  (let [actions (or actions [])
        actions (if template
                  (circle.backend.build.template/apply-template template actions)
                  actions)]
    (build/build (merge args
                        {:vcs_url (-> test-project :vcs_url)
                         :vcs_revision "1234567890foobar"
                         :node (or node (localhost-ssh-map))
                         :notify_emails (or notify_emails [])
                         :actions actions
                         :subject "dummy commit message"}))))

(defn clear-test-db []
  (mongo/with-mongo (test-db-connection)
    (mongo/drop-database! :mongoid_test_test)))

(defn ensure-test-db []
  (clear-test-db)
  (mongo/with-mongo (test-db-connection)
    (ensure-project test-project)
    (ensure-project yml-project)
    (ensure-project partially-inferred-project)
    (ensure-test-user-and-project)))

(defmacro test-ns-setup
  "Defines a midje (background) such that the test DB is cleared
  between runs, and all clojure DB connections go through the test DB"
  []
  `(background (before :facts (ensure-test-db))
               (around :facts (ruby/with-runtime (ruby/test-ruby)
                                (mongo/with-mongo (test-db-connection)
                                  ?form)))))
