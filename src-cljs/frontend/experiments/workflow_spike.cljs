(ns frontend.experiments.workflow-spike)

(defn status-class [{:keys [status] :as workflow}]
  (case status
    "fail" "fail"
    "success" "pass"
    "running" "busy"
    "canceled" "stop"))

(defn status-icon [{:keys [status] :as workflow}]
  (case (status-class workflow)
    "fail" "Status-Failed"
    "stop" "Status-Canceled"
    "pass" "Status-Passed"
    "busy" "Status-Running"))

(def fake-progress-response
  {:name "mock_workflow_name"
   :created-at "2016-12-15T15:05:41.938-00:00"
   :id "mock-workflow-id"
   :status "running"
   :trigger-resource {:type "git-commit"
                      :data {
                             :vcs_revision "c9790fd2c3a4ef69b94dac403f090e34783c8bde"
                             :vcs_type "github"
                             :vcs_url "https://github.com/circleci/workflows-poc"
                             :branch "master"
                             :reponame "workflows-poc"
                             :username "circleci"
                             :all_commit_details [{:committer_date "2016-11-22T14:46:14Z"
                                                   :body "This is actually just a dummy commit to see if the workflow will be\nkicked off properly by the Git trigger."
                                                   :author_date "2016-11-22T14:46:14Z"
                                                   :committer_email "nwjsmith@gmail.com"
                                                   :commit "c9790fd2c3a4ef69b94dac403f090e34783c8bde"
                                                   :committer_login "nwjsmith"
                                                   :committer_name "Nate Smith"
                                                   :subject "Explain what this is"
                                                   :commit_url "https://github.com/circleci/workflows-poc/commit/c9790fd2c3a4ef69b94dac403f090e34783c8bde"
                                                   :author_login "nwjsmith"
                                                   :author_name "Nate Smith"
                                                   :author_email "nwjsmith@gmail.com"}]
                             :pull_requests [{:head_sha "acd00b3c1f4bb6bfc009dc1e6220df9a92e8da16",
                                              :url "https://github.com/circleci/workflows-conductor/pull/8"}]}}
   :jobs [{:name "build"
           :type "picard"
           :id "e36d2025-e3f9-47c5-bdca-2259efe44685"
           :status "success"
           :data {:usage_queued_at "2016-12-13T14:44:00.620Z"
                  :committer_name "Nate Smith"
                  :why "api"
                  :parallel 1
                  :committer_email "nwjsmith@gmail.com"
                  :vcs_tag nil
                  :username "circleci"
                  :start_time "2016-12-13T14:42:46.837Z"
                  :build_num 169
                  :stop_time "2016-12-13T14:42:47.551Z"
                  :author_date "2016-11-22T14:46:14Z"
                  :build_time_millis 714
                  :outcome "success"
                  :lifecycle "finished"
                  :vcs_revision "c9790fd2c3a4ef69b94dac403f090e34783c8bde"
                  :build_url "https://circleci.com/gh/circleci/workflows-poc/169"
                  :pull_requests []
                  :fleet "picard"
                  :vcs_url "https://github.com/circleci/workflows-poc"
                  :status "success"
                  :author_name "Nate Smith"
                  :author_email "nwjsmith@gmail.com"
                  :committer_date "2016-11-22T14:46:14Z"
                  :branch "master"
                  :reponame "workflows-poc"
                  :body
                  "This is actually just a dummy commit to see if the workflow will be\nkicked off properly by the Git trigger."
                  :queued_at "2016-12-13T14:42:44.585Z"
                  :user
                  {:name "Le Wang"
                   :vcs_type "github"
                   :login "lewang"
                   :id 586604
                   :is_user true
                   :avatar_url "https://avatars.githubusercontent.com/u/586604?v=3"}
                  :subject "Explain what this is"
                  :dont_build nil}}
          {
           :name "test"
           :type "picard"
           :id "9f435bd9-2086-447d-af55-e0701c95fd14"
           :status "success"
           :data {:usage_queued_at "2016-12-13T14:43:43.584Z"
                  :committer_name "Nate Smith"
                  :why "api"
                  :parallel 1
                  :committer_email "nwjsmith@gmail.com"
                  :vcs_tag nil
                  :username "circleci"
                  :start_time "2016-12-13T14:42:49.410Z"
                  :build_num 170
                  :stop_time "2016-12-13T14:42:50.199Z"
                  :author_date "2016-11-22T14:46:14Z"
                  :build_time_millis 789
                  :outcome "success"
                  :lifecycle "finished"
                  :vcs_revision "c9790fd2c3a4ef69b94dac403f090e34783c8bde"
                  :build_url "https://circleci.com/gh/circleci/workflows-poc/170"
                  :pull_requests []
                  :fleet "picard"
                  :vcs_url "https://github.com/circleci/workflows-poc"
                  :status "success"
                  :author_name "Nate Smith"
                  :author_email "nwjsmith@gmail.com"
                  :committer_date "2016-11-22T14:46:14Z"
                  :branch "master"
                  :reponame "workflows-poc"
                  :body
                  "This is actually just a dummy commit to see if the workflow will be\nkicked off properly by the Git trigger."
                  :queued_at "2016-12-13T14:42:47.550Z"
                  :user
                  {:name "Le Wang"
                   :vcs_type "github"
                   :login "lewang"
                   :id 586604
                   :is_user true
                   :avatar_url "https://avatars.githubusercontent.com/u/586604?v=3"}
                  :subject "Explain what this is"
                  :dont_build nil}}
          {:name "deploy"
           :type "picard"
           :id "9f435bd9-2086-447d-af55-e0701c95fd14"
           :status "waiting"}]})
