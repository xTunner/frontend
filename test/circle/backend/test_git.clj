(ns circle.backend.test-git
  (:use midje.sweet)
  (:use circle.backend.git)
  (:require [circle.model.project :as project])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.backend.github-url :as github])
  (:require fs))

(test/test-ns-setup)

(fact "clone works"
  (let [test-url (-> test/test-project :vcs_url)
        test-git-url (github/->ssh test-url)
        test-repo-path (default-repo-path test-url)
        ssh-key (project/ssh-key-for-url test-url)]
    (fs/deltree test-repo-path)
    (repo-exists? test-repo-path) => false
    (clone test-git-url :ssh-key ssh-key :path test-repo-path)
    (repo-exists? test-repo-path) => true
    (fs/exists? (fs/join test-repo-path "README.md")) => true))

(def known-commit "4ae57e3c6b6425465b7dd1d7ca2bb512777a927b")

(fact "commit-details works"
  (commit-details "repos/arohner/CircleCI" known-commit) =>
  {:subject "Create a guest user when adding repos, allow the guest user to set username & password at the end of the wizard"
   :committer_date "1323636643"
   :author_name "Allen Rohner"
   :parents ["1d3e617ac3abd287d63393d1ee03f13e4801b7c4"]
   :author_email "arohner@gmail.com"
   :author_date "1323636643"
   :committer_name "Allen Rohner"
   :body ""
   :branch "remotes/origin/nginx_ssl~68"
   :committer_email "arohner@gmail.com"})

(fact "committer-email works"
  (committer-email "." known-commit) => "arohner@gmail.com")