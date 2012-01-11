(ns circle.backend.test-git
  (:use midje.sweet)
  (:use circle.backend.git)
  (:require [circle.model.project :as project])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.backend.github-url :as github])
  (:require fs))

(test/ensure-test-project)

(fact "clone works"
  (let [test-url (-> test/test-project :vcs_url)
        test-git-url (github/->ssh test-url)
        test-repo-path (default-repo-path test-url)
        ssh-key (project/ssh-key-for-url test-url)]
    (fs/deltree test-repo-path)
    (repo-exists? test-repo-path) => false
    (clone test-git-url :ssh-key ssh-key :path test-repo-path)
    (repo-exists? test-repo-path) => true))