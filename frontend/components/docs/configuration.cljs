(ns frontend.components.docs.configuration)

(def article
  {:title "Configuring CircleCI"
   :last-updated "May 2, 2013"
   :url :configuration
   :content [:div
             [:p
              "CircleCI automatically infers your settings from your code, so CircleCI's normal processing works just fine in most circumstances.When it doesn't, the"
              [:code "circle.yml"]
              "file makes it easy to tell CircleCI what you need.This is a simple YAML file where you spell out any tweaks required for your web app.You place the file in your git repo's root directory and CircleCI reads the file each time it runs a build."]
             [:p
              "If you want a quick look at how to set up your"
              [:code "circle.yml"]
              "file, check out our"]
             [:p
              "Should you have a test failure, our"
              [:a {:href "/docs/troubleshooting"} "troubleshooting section"]
              "can likely tell you the best way to solve the problem.If you find yourself repeateadly consulting this guide, please + $c(HAML['contact_us']())and let us know what you're working on.We'll try to make it easier for you."]
             [:h2#phases "File structure and content"]
             [:p
              "The"
              [:code "circle.yml"]
              "file is made up of six primary sections.Each section represents a"
              [:em "phase"]
              "of running your tests:"]
             [:ul
              [:li
               [:b "machine"]
               ": adjusting the VM to your preferences and requirements"]
              [:li [:b "checkout"] ": checking out and cloning your git repo"]
              [:li
               [:b "dependencies"]
               ": setting up your project's language-specific dependencies"]
              [:li [:b "database"] ": preparing the databases for your tests"]
              [:li [:b "test"] ": running your tests"]
              [:li [:b "deployment"] ": deploying your code to your web servers"]]
             [:p
              "The"
              [:code "circle.yml"]
              "file contains another "
              [:b "general"]
              " section for general build-related configurationsthat are not related to a specific phase."]
             [:p
              "Remember: most projects won't need to specify anything for many of the phases."]
             [:p
              "The sections contain lists of bash commands.  If you don't specifycommands, CircleCI infers them from your code.  Commands are run inthe order they appear in the file; all test commands are run tocompletion, but a non-zero exit code during setup will cause thebuild to fail early.  You can modify which—andwhen—commands are run by adding "
              [:code "override"]
              ","
              [:code "pre"]
              " and/or "
              [:code "post"]
              " to adjust CircleCI'sinferred commands.  Here's how it works:"]
             [:ul
              [:li
               [:b "pre"]
               ": commands run before CircleCI's inferred commands"]
              [:li
               [:b "override"]
               ": commands run instead of CircleCI's inferred commands"]
              [:li
               [:b "post"]
               ":  commands run after CircleCI's inferred commands"]]
             [:p
              "Each command is run in a separate shell.As such, they do not share an environment with their predecessors, so be aware that"
              [:code "export foo=bar"]
              "in particular does not work.If you'd like to set an environment variable globally, you can specify them in the "
              [:a {:href "\\#machine\\"} "Machine configuration"]
              " section, described below."]
             [:h4 "Modifiers"]
             [:p
              "You can tweak individual commands by adding a modifier.Allowed modifiers are:"]
             [:ul
              [:li
               [:b "timeout"]
               ": if a command runs this many seconds without output, kill it (default:180s)"]
              [:li
               [:b "pwd"]
               ": run commands using this value as the current working directory (default: the checkout directory named for your project, except in the "
               [:code "machine"]
               " and "
               [:code "checkout/pre"]
               " sections, where it defaults to $HOME.)"]
              [:li
               [:b "environment"]
               ": a hash creating a list of environment variables set for this command(see "
               [:a {:href "\\#machine\\"} "Machine configuration"]
               " for this modifier's properties when used in the "
               [:code "machine"]
               " section of the file)"]
              [:li
               [:b "parallel"]
               ": (only used with commands in the "
               [:code "test"]
               " section)if you have "
               [:a
                {:href "\\/docs/parallel-manual-setup\\"}
                " manually set up parallelism"]
               ", set this to true to run a command across all VMs"]
              [:li
               [:b "files"]
               ":The files identified by the file list (or globs) will be appended to thecommand arguments. The files will be distributed across all containersrunning the build. Check"
               [:a
                {:href "\\/docs/parallel-manual-setup#auto-splitting\\"}
                "manual parallelism setup document"]
               " for more details."]
              [:li
               [:b "background"]
               ": when \\true\\, runs a command in the background.  It is similar to ending a shell command with '&', but works correctly over ssh.  Useful for starting servers, which your tests will connect to."]]
[:p
 "Note that YAML is very strict about indentation each time you add a new property.For that reason, modifiers must be indented one level from their command.In the following example, we treat the"
 [:code "bundle install"]
 "command as a key, with "
 [:code "timeout"]
 ", "
 [:code "environment"]
 ", and "
 [:code "pwd"]
 " as the command's hash values."]
[:pre
 "’‘"
 [:code.no-highlight
  "’dependencies:  override:    - bundle install:        timeout: 240        environment:          foo: bar          foo2: bar2        pwd:          test_dir‘"]
 "’‘"]
[:p]
[:h2#machine "Machine configuration"]
[:p
 "The"
 [:code "machine"]
 "section enables you to configure the virtual machine that runs your tests."]
[:p
 "Here's an illustration of the types of things you might typically set in the"
 [:code "machine"]
 "section of the file."]
[:pre
 "’‘"
 [:code.no-highlight
  "’machine:  timezone:    America/Los_Angeles  ruby:    version: 1.9.3-p0-falcontest:  post:    \\- bundle exec rake custom:test:suite‘"]
 "’‘"]
[:p]
[:p
 "This example sets thechooses a"
 [:a {:href "#ruby-version"} "Ruby version"]
 "and patchset,and adds a custom test commandto run after the rest of your commands."]
[:p
 "Although"
 [:code "pre"]
 "and"
 [:code "post"]
 "are supported in the"
 [:code "machine"]
 "section,"
 [:code "override"]
 "is not.Here's how you might adjust the "
 [:code "circle.yml"]
 " file using"
 [:code "pre"]
 "to install a different version of "
 [:code "phantomjs"]
 " than the version CircleCI has installed."]
[:pre
 "’‘"
 [:code.no-highlight
  "’machine:  pre:    - curl -k -L -o phantomjs.tar.bz2 http://phantomjs.googlecode.com/files/phantomjs-1.8.2-linux-x86_64.tar.bz2    - tar -jxf phantomjs.tar.bz2‘"]
 "’‘"]
[:p]
[:h3 "Environment"]
[:p
 "You set environment variables for "
 [:b "all commands"]
 " in the build by adding"
 [:code "environment"]
 "to the"
 [:code "machine"]
 "section.Remember that CircleCI uses a new shell for every command; as previously mentioned"
 [:code "export foo=bar"]
 "won't work. Instead, you must include something like this."]
[:pre
 "’‘"
 [:code.no-highlight
  "’machine:  environment:    foo: bar    baz: 123‘"]
 "’‘"]
[:p
 "If you don't want to use this method, there are"
 [:a
  {:href "\\/docs/environment-variables\\"}
  "a number of other options"]
 "."]
[:h3 "Timezone"]
[:p
 "The machine's time zone is UTC by default.You use"
 [:code "timezone"]
 "to adjust to the same time zone as your "
 [:i "production"]
 " server.Changing the time to your "
 [:i "development"]
 " machine's time zone is "
 [:b "asking for trouble"]
 "."]
[:p
 "This modifier tells CircleCI tooverwrite"
 [:code "/etc/timezone"]
 "and then restart all databases and services that rely on it.This modifier supports any time zone listed in the IANA time zone database.You can find this by looking in"
 [:code "/usr/share/zoneinfo/"]
 "on your Unix machine or in the"
 [:strong "TZ"]
 "column in"]
[:p
 "Be aware that some developers, especially those that collaborate across different time zones, do use UTC on their production servers.This alternative can avoid horrific Daylight Saving Time (DST) bugs."]
[:h3 "Hosts"]
[:p
 "Sometimes you might need to add one or more entries to the"
 [:code "/etc/hosts"]
 "file to assign various domain names to an IP address.This example points to the development subdomain at the circleci hostname and IP address."]
[:pre
 "’‘"
 [:code.no-highlight
  "’machine:  hosts:    dev.circleci.com: 127.0.0.1    foobar: 1.2.3.4‘"]
 "’‘"]
[:p]
[:h3 "Ruby version"]
[:p
 "CircleCI uses"
 [:a {:href "https://rvm.io/"} "RVM"]
 "to manage Ruby versions.We use the Ruby version you specify in your "
 [:code ".rvmrc"]
 ", your"
 [:code ".ruby-version"]
 "file, or your Gemfile.If you don't have one of these files,we'll use Ruby"
 [:code " + $e($c(CI.Versions.default_ruby))"]
 "orwhichever we think is better.If you use a different Ruby version let CircleCI know by including that information in the"
 [:code "machine"]
 "section. Here's an example of how you do that."]
[:pre
 "’‘"
 [:code.no-highlight "’machine:  ruby:    version: 1.9.3-p0-falcon‘"]
 "’‘"]
[:p "The complete list of supported Ruby versions is found"]
[:h3#node-version "Node.js version"]
[:p
 "CircleCI uses"
 [:a {:href "https://github.com/creationix/nvm"} "NVM"]
 "to manage Node versions.See"
 [:a {:href "/docs/environment#nodejs"} "supported Node versions"]
 "for a complete list.If you do not specify a version, CircleCI usesNote that recent versions of NVM support selecting versions throughpackage.json.If your version of NVM supports this, we recommend you use it."]
[:p
 "Here's an example of how to set the version of Node.js to be used for your tests."]
[:pre
 "’‘"
 [:code.no-highlight "’machine:  node:    version: 0.6.18‘"]
 "’‘"]
[:p]
[:h3 "Java version"]
[:p
 "Here's an example of how to set the version of Java to be used for your tests."]
[:pre
 "’‘"
 [:code.no-highlight "’machine:  java:    version: openjdk7‘"]
 "’‘"]
[:p
 "The default version of Java is "
 [:code "oraclejdk7"]
 ".See"
 [:a {:href "/docs/environment#java"} "supported Java versions"]
 "for a complete list."]
[:h3 "PHP version"]
[:p
 "CircleCI uses"
 [:a {:href "https://github.com/CHH/php-build"} "php-build"]
 "and"
 [:a {:href "https://github.com/CHH/phpenv"} "phpenv"]
 "to manage PHP versions.Here's an example of how to set the version of PHP used for your tests."]
[:pre
 "’‘"
 [:code.no-highlight "’machine:  php:    version: 5.4.5‘"]
 "’‘"]
[:p]
[:p
 "See"
 [:a {:href "/docs/environment#php"} "supported PHP versions"]
 "for a complete list."]
[:h3 "Python version"]
[:p
 "CircleCI uses"
 [:a {:href "https://github.com/yyuu/pyenv"} "pyenv"]
 "to manage Python versions.Here's an example of how to set the version of Python used for your tests."]
[:pre
 "’‘"
 [:code.no-highlight "’machine:  python:    version: 2.7.5‘"]
 "’‘"]
[:p]
[:p
 "See"
 [:a {:href "/docs/environment#python"} "supported Python versions"]
 "for a complete list."]
[:h3 "GHC version"]
[:p
 "You can choose from a"
 [:a
  {:href "/docs/configuration#Haskell"}
  "number of available GHC versions"]
 "in your "
 [:code "circle.yml"]
 ":"]
[:h3 "Other languages"]
[:p
 "Our"
 [:a {:href "/docs/environment"} "test environment"]
 "document has more configuration information about"
 [:a {:href "/docs/environment#other"} "other languages"]
 "including"
 [:a {:href "\\/docs/environment#python\\"} "Python"]
 ","
 [:a {:href "\\/docs/environment#clojure\\"} "Clojure"]
 ","
 [:a {:href "\\/docs/environment#other\\"} "C/C++"]
 ","
 [:a {:href "\\/docs/environment#other\\"} "Golang"]
 "and"
 [:a {:href "\\/docs/environment#other\\"} "Erlang"]
 "."]
[:h3#services "Databases and other services"]
[:p
 "CircleCI supports a large number of"
 [:a
  {:href "\\/docs/environment#databases\\"}
  "databases and other services"]
 ".Most popular ones are running by default on our build machines (bound to localhost), including Postgres, MySQL, Redis and MongoDB."]
[:p
 "You can enable other databases and services from the"
 [:code "services"]
 "section:"]
[:pre
 "’‘"
 [:code
  "’machine:  services:    - cassandra    - elasticsearch    - rabbitmq-server    - riak    - beanstalkd    - couchbase-server    - neo4j    - sphinxsearch‘"]
 "’‘"]
[:h2#checkout "Code checkout from GitHub"]
[:p
 "The "
 [:code "checkout"]
 " section is usually pretty vanilla, but we include examples of common things you might need to put in the section.You can modify commands by including "
 [:code "override"]
 ", "
 [:code "pre"]
 ", and/or "
 [:code "post"]
 "."]
[:div.top-caption "Example: using git submodules"]
[:pre
 "’‘"
 [:code.bash
  "’checkout:  post:    - git submodule sync    - git submodule update --init‘"]
 "’‘"]
[:p]
[:div.top-caption
 "Example: overwriting configuration files on CircleCI"]
[:pre
 "’‘"
 [:code.bash
  "’checkout:  post:    - mv config/.app.yml config/app.yml‘"]
 "’‘"]
[:h2#dependencies "Project-specific dependencies"]
[:p
 "Most web programming languages and frameworks, including Ruby's bundler, npm for Node.js, and Python's pip, have some form of dependency specification;CircleCI automatically runs commands to fetch such dependencies."]
[:p
 "You can use "
 [:code "override"]
 ", "
 [:code "pre"]
 ", and/or "
 [:code "post"]
 " to modify"
 [:code "dependencies"]
 "commands.Here are examples of common tweaks you might make in the"
 [:code "dependencies"]
 "section."]
[:div.top-caption "Example: using npm and Node.js"]
[:pre
 "’‘"
 [:code.bash "’dependencies:  override:    - npm install --dev‘"]
 "’‘"]
[:div.top-caption "Example: using a specific version of bundler"]
[:pre
 "’‘"
 [:code.bash
  "’dependencies:  pre:    - gem uninstall bundler    - gem install bundler --pre‘"]
 "’‘"]
[:h3#bundler "Bundler flags"]
[:p
 "If your project includes bundler (the dependency management program for Ruby), you can include"
 [:code "without"]
 "to list dependency groups to be excluded from bundle install.Here's an example of what that would look like."]
[:pre
 "’‘"
 [:code.bash
  "’dependencies:  bundler:    without: [production, osx]‘"]
 "’‘"]
[:h3#cache-directories "Custom Cache Directories"]
[:p
 "CircleCI caches dependencies between builds.To include any custom directories in our caching, you can use"
 [:code "cache_directories"]
 "to list any additional directories you'd like cached between builds.Here's an example of how you could cache two custom directories."]
[:pre
 "’‘"
 [:code.bash
  "’dependencies:  cache_directories:    - \\assets/cache\\    # relative to the build directory    - \\~/assets/output\\ # relative to the user's home directory‘"]
 "’‘"]
[:p "Caches are private, and are not shared with other projects."]
[:h2#database "Database setup"]
[:p
 "Your web framework typically includes commands to create your database, install your schema, and run your migrations.You can use "
 [:code "override"]
 ", "
 [:code "pre"]
 ", and/or "
 [:code "post"]
 " to modify"
 [:code "database"]
 "commands.See "
 [:a
  {:href "\\/docs/manually#databases\\"}
  "Setting up your test database"]
 " for more information."]
[:p
 "If our inferred "
 [:code "database.yml"]
 " isn't working for you, you may need to "
 [:code "override"]
 " our setup commands (as shown in the following example).If that is the case, please + $c(HAML[contact_us]())and let Circle know so that we can improve our inference."]
[:p]
[:pre
 "’  ‘"
 [:code.bash
  "’  database:  override:    - mv config/database.ci.yml config/database.yml    - bundle exec rake db:create db:schema:load --trace  ‘"]
 "’‘"]
[:p
 "FYI, you have the option of pointing to the location of your stored database config file using the "
 [:code "environment"]
 " modifier in the"
 [:code "machine"]
 "section."]
[:p]
[:pre
 "’  ‘"
 [:code.bash
  "’  machine:  environment:    DATABASE_URL: postgres://ubuntu:@127.0.0.1:5432/circle_test  ‘"]
 "’‘"]
[:h2#test "Running your tests"]
[:p
 "The most important part of testing is actually running the tests!"]
[:p
 "CircleCI supports the use of "
 [:code "override"]
 ", "
 [:code "pre"]
 ", and/or "
 [:code "post"]
 " in the "
 [:code "test"]
 " section.However, this section has one minor difference: all test commands will run, even if one fails.This allows our test output to tell you about all the tests that fail, not just the first error."]
[:div.top-caption "Example: running spinach after RSpec"]
[:pre
 "’‘"
 [:code.bash
  "’test:  post:    - bundle exec rake spinach:        environment:          RAILS_ENV: test‘"]
 "’‘"]
[:div.top-caption "Example: running phpunit on a special directory"]
[:pre
 "’‘"
 [:code.bash
  "’test:  override:    - phpunit my/special/subdirectory/tests‘"]
 "’‘"]
[:p
 "CircleCI also supports the use of "
 [:code "minitest_globs"]
 "(a list of file globs, using "
 [:a
  {:href "\\http://ruby-doc.org/core-2.0/Dir.html#glob-method\\"}
  "Ruby's Dir.glob syntax"]
 ")that can list the file globs to be used during testing."]
[:p]
[:p
 "By default, when testing in parallel, CircleCI runs all tests in the test/unit, test/integration, andtest/functional directories.You can add "
 [:code "minitest_globs"]
 " to replace thestandard directories with your own.This is needed only when you have additional or non-standardtest directories and you are testing in parallel with MiniTest."]
[:div.top-caption "Example: minitest_globs"]
[:pre
 "’‘"
 [:code.bash
  "’test:  minitest_globs:    - test/integration/**/*.rb    - test/extra-dir/**/*.rb‘"]
 "’‘"]
[:p]
[:h2 "Deployment"]
[:p
 "The"
 [:code "deployment"]
 "section is optional. You can run commands to deploy to staging or production.These commands are triggered only after a successful (green) build."]
[:pre
 "’‘"
 [:code.bash
  "’deployment:  production:    branch: production    commands:      - ./deploy_prod.sh  staging:    branch: master    commands:      - ./deploy_staging.sh‘"]
 "’‘"]
[:p
 "The"
 [:code "deployment"]
 "section consists of multiple subsections. In the example shown above, thereare two—one named "
 [:i "production"]
 " and one named "
 [:i "staging"]
 ".Subsection names must be unique.Each subsection can list multiple branches, but at least one of these fields must benamed "
 [:i "branch"]
 ". In instances of multiple branches, the first one that matchesthe branch being built is the one that is run.In the following example, if a developer pushes to any of the three branches listed, the script"
 [:code "merge_to_master.sh"]
 "is run."]
[:pre
 "’‘"
 [:code.bash
  "’deployment:  automerge:    branch: [dev_alice, dev_bob, dev_carol]    commands:      - ./merge_to_master.sh‘"]
 "’‘"]
[:p
 "The "
 [:i "branch"]
 " field can also specify regular expressions, surrounded with"
 [:code "/"]
 "(e.g."
 [:code "/feature_.*/"]
 "):"]
[:pre
 "’‘"
 [:code.bash
  "’deployment:  feature:    branch: /feature_.*/    commands:      - ./deploy_feature.sh‘"]
 "’‘"]
[:p]
[:h3 "SSH Keys"]
[:p
 "If deploying to your servers requires SSH access, you'll need toupload the keys to CircleCI.CircleCI's UI enables you to do this on your project's "
 [:b "Edit settings > SSH keys"]
 " page.Add and then submit the one or more SSH keys neededfor deploying to your machines. If you leave the "
 [:b "Hostname"]
 " field blank,the public key will be used for all hosts."]
[:h3 "Heroku"]
[:p
 "CircleCI also has first-class support for deploying to Heroku.Specify the app you'd like to"
 [:code "git push"]
 "to under "
 [:code "appname"]
 ".Upon a successful build, we'll automatically deploy to the app in the section that matches the push, if there is one."]
[:pre
 "’‘"
 [:code.bash
  "’deployment:  staging:    branch: master    heroku:      appname: foo-bar-123‘"]
 "’‘"]
[:p
 "Setting up our deployment to Heroku requires one extra step.Due to Heroku's architecture and security model, we need to deploy as a particular user.A member of your project, possibly you, will need to register as that user.CircleCI's UI enables you to do this on your project's "
 [:b "Edit settings > Heroku settings"]
 " page."]
[:h3#heroku-extra "Heroku with pre or post-deployment steps"]
[:p
 "If you want to deploy to Heroku and also run commands before or after the deploy, you must use the 'normal' deployment syntax."]
[:pre
 "’‘"
 [:code.bash
  "’  deployment:    production:      branch: production      commands:        - git push git@heroku.com:foo-bar-123.git $CIRCLE_SHA1:master        - heroku run rake db:migrate --app foo-bar-123‘"]
 "’‘"]
[:p]
[:h2#notify "Notifications"]
[:p "CircleCI sends personalized notifications by email."]
[:p
 "In addition to these per-user emails, CircleCI sends notifications on a per-project basis.CircleCI supports sending webhooks when your build completes.CircleCI also supports HipChat, Campfire, Flowdock and IRC notifications; you configure these notifications from your project's"
 [:b "Edit settings > Notifications "]
 " page."]
[:p "This example sends a JSON packet to the specified URL."]
[:pre
 "’‘"
 [:code
  "’notify:  webhooks:    # A list of hook hashes, containing the url field    - url: https://example.com/hooks/circle‘"]
 "’‘"]
[:p
 "The JSON packet is identical to the result of the"
 [:a {:href "/docs/api#build"} "Build API"]
 "call for the same build, except that it is wrapped in a \\payload\\ key:"]
[:pre
 "’‘"
 [:code
  "’{  \\payload\\: {    \\vcs_url\\ : \\https://github.com/circleci/mongofinil\\,    \\build_url\\ : \\https://circleci.com/gh/circleci/mongofinil/22\\,    \\build_num\\ : 22,    \\branch\\ : \\master\\,    ...  }}‘"]
 "’‘"]
[:p]
[:h2#branches "Specifying branches to build"]
[:p
 "CircleCI by default tests every push to "
 [:i "any"]
 " branch in the repository.Testing all branches maintains quality in all branches and addsconfidence when the branches are to be merged with default branch."]
[:p
 "You may, however, blacklist branches from being built in CircleCI.  This exampleexcludes"
 [:code "gh-pages"]
 "from being built in circle:"]
[:pre
 "’‘"
 [:code
  "’general:  branches:    ignore:      - gh-pages # list of branches to ignore      - /release\\\\/.*/ # or ignore regexes‘"]
 "’‘"]
[:p
 "You may also whitelist branches, so only whitelisted branches will trigger a build.This example limit builds in circle to"
 [:code "master"]
 "and"
 [:code "feature-.*"]
 "branches:"]
[:pre
 "’‘"
 [:code
  "’general:  branches:    only:      - master # list of branches to build      - /feature-.*/ # or regexes‘"]
 "’‘"]
[:p
 "We discourage branch whitelisting, it means work-in-progresscode can go a long time without being integrated and tested and we've foundit leads to problems when that untested code gets merged."]
[:p
 [:code "circle.yml"]
 "is per-branch configuration file, and the branch ignore list in one branch willonly affect that branch and no other one."]
[:h2#build-dir "Specifying build directory"]
[:p
 "Circle runs all commands on the repository root, by default.  However, ifyou store your application code in a subdirectory instead of the root, youcan specify the build directory in circle.yml.  For example, to set the builddirectory to `api` sub-directory, you can use the following configuration:"]
[:pre "’‘" [:code "’general:  build_dir: api‘"] "’‘"]
[:p
 "Circle will run its inference as well as all build commands from that directory."]
[:h2#help "Need anything else?"]
[:p
 "We are adding support for configuring every part of your build.If you need to tweak something that isn't currently supported, please + $c(HAML[contact_us]())and we'll figure out how to make it happen."]]})
