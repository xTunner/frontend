(ns circle.backend.test-git
  (:use midje.sweet)
  (:use circle.backend.git)
  (:require [circle.model.project :as project])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.backend.github-url :as github])
  (:require [circle.backend.build.config :as config])
  (:require fs))

(test/test-ns-setup)


(fact "clone works"
  (let [test-url (-> test/test-project :vcs_url)
        test-git-url (github/->ssh test-url)
        test-repo-path (default-repo-path test-url)
        ssh-key (project/ssh-key-for-url test-url)]
    (fs/deltree test-repo-path) => anything
    (repo-exists? test-repo-path) => false
    (clone test-git-url :ssh-key ssh-key :path test-repo-path) => anything
    (repo-exists? test-repo-path) => true
    (fs/exists? (fs/join test-repo-path "README.md")) => true))

;; This is from circleci/circle
(def known-commit "ab2e274b5a3493b49b1f136432e74fe80c320477")

(fact "commit-details works"
  (commit-details "." known-commit) =>
  {:subject "Use the test-yml repo, commits from it, and the correct url."
   :committer_date "1329447089"
   :author_name "Paul Biggar"
   :parents ["4b0389fa94e50cb4b112fd2daec99cf3e6f526e8"]
   :author_email "paul.biggar@gmail.com"
   :author_date "1329447089"
   :committer_name "Paul Biggar"
   :body ""
   :branch #"master~\d+"
   :committer_email "paul.biggar@gmail.com"})

(fact "committer-email works"
  (committer-email "." known-commit) => "paul.biggar@gmail.com")

(defn test-ensure-repo [url]
  (let [git-url (github/->ssh url)
        repo-path (default-repo-path url)
        ssh-key (project/ssh-key-for-url url)]
    (fs/deltree repo-path)
    (clone git-url :ssh-key ssh-key :path repo-path)))

(fact "latest-commit works"
  (let [url (-> test/test-project :vcs_url)
        _ (test-ensure-repo url)
        repo (default-repo-path url)]
    (latest-commit repo) => "17978c70c6a14eb0a47fee3b1d2f36bd34b7bcf9"))

(fact "latest-commit-current-branch works"
  (let [url (-> test/test-project :vcs_url)
        _ (test-ensure-repo url)
        repo (default-repo-path url)]
    (latest-commit-current-branch repo) => "78f58846a049bb6772dcb298163b52c4657c7d45"))