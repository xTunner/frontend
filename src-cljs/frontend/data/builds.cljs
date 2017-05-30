(ns frontend.data.builds)

; TODO: use better test data
(def builds
  [{:committer_name "GitHub"
    :why "api"
    :parallel 1
    :committer_email "noreply @github.com"
    :username "circleci"
    :start_time "2017-05-26T16:00:52-07:00"
    :build_num 167596
    :stop_time "2017-05-26T16:00:52-07:00"
    :author_date "2017-05-26T16:00:52-07:00"
    :build_time_millis 408040
    :outcome "success"
    :lifecycle "finished"
    :vcs_revision "88098a"
    :build_url "https://circleci.com/gh/circleci/circle/167596"
    :pull_requests [{:head_sha "283072b34bb7200ee3da69e4da7800fb3f9e33f1"
                     :url "https://github.com/facebook/react-native/pull/14211"}]
    :fleet "ubuntu-12.04"
    :vcs_url "https://github.com/circleci/circle"
    :status "success"
    :author_name "Mahmood Ali"
    :author_email "mahmood @circleci.com"
    :committer_date "2017-05-26T16:00:52-07:00",
    :branch "enterprise-production",
    :reponame "circle",
    :body "[CIRCLE-4559] Support Server side encryption",
    :queued_at "2017-05-26T16:00:52-07:00"
    :user {:is_user true, :login "gordonsyme", :avatar_url "https://avatars.githubusercontent.com/u /1714741?v=3", :name "Gordon Syme", :vcs_type "github", :id 1714741}
    :subject "Merge pull request #7919 from circleci/storage-136"
    :dont_build nil
    :platform 1.0
    }
   {:committer_name "GitHub"
    :why "api"
    :parallel 1
    :committer_email "noreply @github.com"
    :username "circleci"
    :start_time "2017-05-26T16:00:52-07:00"
    :build_num 167596
    :stop_time "2017-05-26T16:00:52-07:00"
    :author_date "2017-05-26T16:00:52-07:00"
    :build_time_millis 408040
    :outcome "success"
    :lifecycle "finished"
    :vcs_revision "88098a"
    :build_url "https://circleci.com/gh/circleci/circle/167596"
    :pull_requests [{:head_sha "283072b34bb7200ee3da69e4da7800fb3f9e33f1"
                     :url "https://github.com/facebook/react-native/pull/14211"}]
    :fleet "ubuntu-12.04"
    :vcs_url "https://github.com/circleci/circle"
    :status "success"
    :author_name "Mahmood Ali"
    :author_email "mahmood @circleci.com"
    :committer_date "2017-05-26T16:00:52-07:00",
    :branch "enterprise-production",
    :reponame "circle",
    :body "[CIRCLE-4559] Support Server side encryption",
    :queued_at "2017-05-26T16:00:52-07:00"
    :user {:is_user true, :login "gordonsyme", :avatar_url "https://avatars.githubusercontent.com/u /1714741?v=3", :name "Gordon Syme", :vcs_type "github", :id 1714741}
    :subject "Merge pull request #7919 from circleci/storage-136"
    :dont_build nil
    :platform 1.0
}])