(ns frontend.state)

(defn initial-state []
  {:environment "development"
   :settings {:projects {}
              :add-projects {:repo-filter-string ""}}
   :navigation-point :dashboard
   :current-user {:plan "p19",
                  :user_key_fingerprint "9c:ae:90:91:0d:e7:78:0a:9f:eb:f6:2b:11:7f:20:d3",
                  :heroku_api_key "8476f325-af7f-4afe-9698-f9f6add3f5a0",
                  :selected_email "dwwoelfel@gmail.com",
                  :containers 8,
                  :gravatar_id "c0ca7580659419b084d64cc3c3e8d83c",
                  :name "Daniel Woelfel",
                  :sign_in_count 356,
                  :all_emails ["dwwoelfel@gmail.com"],
                  :basic_email_prefs "none",
                  :trial_end "2013-10-05T22:44:41.158Z",
                  :created_at "2013-06-18T04:15:54.063Z",
                  :login "dwwoelfel",
                  :parallelism 1,
                  :days_left_in_trial -181,
                  :github_id 476818,
                  :github_oauth_scopes ["user:email" "repo"],
                  :admin true
                  :organizations [{:org true, :avatar_url "https://avatars.githubusercontent.com/u/587399?", :login "cakehealth"}
                                  {:org true, :avatar_url "https://avatars.githubusercontent.com/u/1231870?", :login "circleci"}
                                  {:org true, :avatar_url "https://avatars.githubusercontent.com/u/4412941?", :login "heavybit"}]
                  :collaborators [{:avatar_url "https://avatars.githubusercontent.com/u/476818?", :login "dwwoelfel"}
                                  {:avatar_url "https://avatars.githubusercontent.com/u/1231870?", :login "circleci"}
                                  {:avatar_url "https://avatars.githubusercontent.com/u/723711?", :login "enzuru"}
                                  {:avatar_url "https://avatars.githubusercontent.com/u/262801?", :login "muub"}
                                  {:avatar_url "https://avatars.githubusercontent.com/u/4368163?", :login "notnoopci"}
                                  {:avatar_url "https://avatars.githubusercontent.com/u/14567?", :login "pjlegato"}]}
   :crumbs [{:type :org
             :name "circleci"
             :path "/gh/circleci"}
            {:type :project
             :name "circle"
             :active true
             :path "/gh/circleci/circle"}
            {:type :settings
             :name "Edit settings"
             :path "/gh/circleci/circle/edit"}]
   :current-repos nil
   :render-context {:status nil,
                    :githubPrivateAuthURL "https://github.com/login/oauth/authorize?client_id=78a2ba87f071c28e65bb&red…2Fgh%252Forganizations%252Fcircleci%252Fsettings&scope=user%3Aemail%2Crepo",
                    :heroku false,
                    :env "production",
                    :assetsRoot "//dmmj3mmt94rvw.cloudfront.net",
                    :githubClientId "78a2ba87f071c28e65bb",
                    :marketo_munchkin_key "894-NPA-635",
                    :flash nil,
                    :from_heroku nil,
                    :githubNoPermAuthURL "https://github.com/login/oauth/authorize?client_id=78a2ba87f071c28e65bb&red…b%3Freturn-to%3D%252Fgh%252Forganizations%252Fcircleci%252Fsettings&scope="}
   :projects []
   :recent-builds []})
