(ns frontend.components.docs.git-bundle-install)

(def article
  {:title "Git errors during bundle install"
   :last-updated "Feb 3, 2013"
   :url :git-bundle-install
   :content [:div
             [:p
              "When your tests run, during the"
              [:code "bundle install"]
              "step, you might see something like this:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’Fetching git@github.com:rails/railsGit error: command `git clone 'git@github.com:rails/rails' \\/home/ubuntu/circle-2/vendor/bundle/ruby/1.9.1/cache/bundler/git/rails-47ba0391b239cf6d20fc732cd925192bcf3430fc\\ --bare --no-hardlinks` in directory /home/ubuntu/circle-1 has failed.Permission denied (publickey).fatal: The remote end hung up unexpectedly‘"]
              "’‘"]
             [:p
              "This happens because you have a git repository listed as a dependency in your Gemfile:"]
             [:pre
              "’‘"
              [:code.ruby
               "’gem \\rails\\, :git => \\git://github.com/rails/rails.git\\‘"]
              "’‘"]
             [:p
              "If the repository is public, just change the dependency to use a"
              [:code "http"]
              "url:"]
             [:pre
              "’‘"
              [:code.ruby
               "’gem \\rails\\, :git => \\http://github.com/rails/rails\\‘"]
              "’‘"]
             [:p
              "If the repository is private, you will need to enable user keysfrom your project's"
              [:strong "Edit settings > GitHub user"]
              "page."]]})
