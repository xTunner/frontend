(ns frontend.components.docs.environment-variables)

(def article
  {:title "Environment variables"
   :last-updated "Feb 2, 2013"
   :url :environment-variables
   :content [:div
             [:p
              "We export a number of environment variables during each build, which you may finduseful for more complex testing or deployment."]
             [:h3 "Basics"]
             [:p
              "Ideally, you won't have code which behaves differently in CI. But for the caseswhen it's necessary, we set two environment variables which you can test:"]
             [:dl
              [:dt [:code "CIRCLECI"]]
              [:dd "true"]
              [:dt [:code "CI"]]
              [:dd "true"]]
             [:h3 "Build Details"]
             [:p
              "We publish the details of the currently running build in these variables:"]
             [:dl
              [:dt [:code "CIRCLE_PROJECT_USERNAME"]]
              [:dd
               "The username or organization name of the project being tested, i.e. \\foo\\ in circleci.com/gh/foo/bar/123"]
              [:dt [:code "CIRCLE_PROJECT_REPONAME"]]
              [:dd
               "The repository name of the project being tested, i.e. \\bar\\ in circleci.com/gh/foo/bar/123"]
              [:dt [:code "CIRCLE_BRANCH"]]
              [:dd "The name of the branch being tested, e.g. 'master'."]
              [:dt [:code "CIRCLE_SHA1"]]
              [:dd "The SHA1 of the commit being tested."]
              [:dt [:code "CIRCLE_COMPARE_URL"]]
              [:dd
               "A link to GitHub's comparison view for this push. Not present for builds that are triggered by GitHub pushes."]
              [:dt [:code "CIRCLE_BUILD_NUM"]]
              [:dd "The build number, same as in circleci.com/gh/foo/bar/123"]
              [:dt [:code "CIRCLE_ARTIFACTS"]]
              [:dd "The directory whose contents are automatically saved as"]]
             [:h3 "Parallelism"]
             [:p "These variables are available for"]
             [:dl
              [:dt [:code "CIRCLE_NODE_TOTAL"]]
              [:dd
               "The total number of nodes across which the current test is running."]
              [:dt [:code "CIRCLE_NODE_INDEX"]]
              [:dd "The index (0-based) of the current node."]]
             [:h3 "Other"]
             [:p
              "There are quite a few other environment variables set. Here are some ofthe ones you might be looking for:"]
             [:dl
              [:dt [:code "HOME"]]
              [:dd "/home/ubuntu"]
              [:dt [:code "DISPLAY"]]
              [:dd ":99"]
              [:dt [:code "LANG"]]
              [:dd "en_US.UTF-8"]
              [:dt [:code "PATH"]]
              [:dd "Includes /home/ubuntu/bin"]]
             [:h3#custom "Set your own!"]
             [:p
              "You can of course set your own environment variables, too!The only gotcha is that each command runs in its own shell, so just adding an"
              [:code "export FOO=bar"]
              "command won't work."]
             [:h4
              "Setting environment variables for all commands using circle.yml"]
             [:p
              "You can set environment variables in your"
              [:code "circle.yml"]
              "file, that"]
             [:h4
              "Setting environment variables for all commands without adding them to git"]
             [:p
              "Occasionally, you'll need to add an API key or some other secret asan environment variable.  You might not want to add the value to yourgit history.  Instead, you can add environment variables using the"
              [:b "Edit settings > Environment Variables"]
              " page of your project."]
             [:p
              "All commands and data on CircleCI's VMs can be accessed by any of your colleagues—we run your arbitrary code, so it is not possible to secure.Take this into account before adding important credentials that some colleagues do not have access to."]
             [:h4 "Per-command environment variables"]
             [:p
              "You can set environment variables per-command as well.You can use standard bash syntax in your commands:"]
             [:pre
              "’‘"
              [:code.bash "’RAILS_ENV=test bundle exec rake test‘"]
              "’‘"]
             [:p
              "You can also use "
              [:a
               {:href "\\/docs/configuration#modifiers\\"}
               "the environment modifier"]
              " in your"
              [:code "circle.yml"]
              "file."]]})
