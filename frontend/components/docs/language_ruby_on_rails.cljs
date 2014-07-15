(ns frontend.components.docs.language-ruby-on-rails)

(def article
  {:title "Continuous Integration and Continuous Deployment with Ruby/Rails"
   :short-title "Ruby/Rails"
   :last-updated "March 12, 2014"
   :url :language-ruby-on-rails
   :content [:div
             [:p
              "CircleCI makes Rails testing simple. During each build, Circle looks at your code,infers your build environment, and runs your tests.The majority of the time, this just works—and works well.Of course, it helps if your project adheres to standard practices(i.e., \\convention over configuration\\) for standard Ruby testing frameworks."]
             [:p
              "You can"
              [:a {:href "/docs/configuration"} "add any custom configuration"]
              "or set up deployment from a"
              [:a {:href "/docs/config-sample"} "circle.yml file"]
              "checked into your repo's root directory."]
             [:h3 "Version"]
             [:p
              "We use"
              [:a {:href "https://rvm.io/"} "RVM"]
              "to provide access to a wide variety ofThe default version of Ruby is either + $e($c(CI.Versions.default_ruby))orwhichever we think is best for your project."]
             [:p
              "You can manually set your Ruby version from your "
              [:code "circle.yml"]
              ":"]
             [:p
              "Our"
              [:a {:href "/docs/environment"} "test environment doc"]
              "covers more details about language versions and tools; it also explains how Circleworks with testing tools that require a browser."]
             [:h3 "Dependencies"]
             [:p
              "If Circle detects a Gemfile, we automatically run "
              [:code "bundle install"]
              ". Yourgems are automatically cached between builds to save time downloading dependencies.You can add additional project dependencies from the"
              [:a
               {:href "\\/docs/configuration#dependencies\\"}
               "dependencies section of your circle.yml"]
              ":"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  post:    - bundle exec rake assets:precompile‘"]
              "’‘"]
             [:h3 "Databases"]
             [:p
              "Circle manages all your database requirements,such as running your "
              [:code "rake"]
              " commands for creating, loading,and migrating your database.We have pre-installed more than a dozenincluding PostgreSQL, MySQL, and MongoDB.You can add custom database commands from the"
              [:a
               {:href "\\/docs/configuration#database\\"}
               "database section of your circle.yml"]
              "."]
             [:h3 "Testing"]
             [:p
              "Circle will automatically infer your test commands if you'reusing Test::Unit, RSpec, Cucumber, Spinach, Jasmine, or Konacha.You can also add additional commands from the "
              [:a
               {:href "\\/docs/configuration#test\\"}
               "test section of your circle.yml"]
              ":"]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  test:  post:    - bundle exec rake test:custom  ‘"]
              "’‘"]
             [:h3 "Testing in Parallel"]
             [:p
              "Should you need faster testing, Circle can automatically split yourtests and run them in parallel across multiple machines.You can enable parallelism on your project's "
              [:b "Edit settings > Parallelism"]
              "page in the Circle UI."]
             [:p
              "Circle can automatically split tests for RSpec, Cucumber, and Test::Unit.For other testing libraries, we have instructions for "
              [:a
               {:href "\\/docs/parallel-manual-setup\\"}
               "manually setting up parallelism"]
              "."]
             [:h3 "Deployment"]
             [:p
              "Circle offers first-class support for deployment to your staging and production environments.When your build is green, Circle will run the commands from the "
              [:a
               {:href "\\/docs/configuration#deployment\\"}
               "deployment section of your circle.yml"]
              "."]
             [:p
              "You can find more detailed instructions in the "
              [:a
               {:href "\\/docs/introduction-to-continuous-deployment\\"}
               "Continuous Deployment doc"]
              "."]
             [:h3 "Troubleshooting for Ruby on Rails"]
             [:p
              "Our"
              [:a {:href "/docs/troubleshooting-ruby"} "Ruby troubleshooting"]
              "documentation has information about the following issues and problems:"]
             " + $c(this.include_article('troubleshooting_ruby'))"
             [:p
              "If you are still having trouble, pleaseand we will be happy to help."]]})

