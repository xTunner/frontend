(ns frontend.components.docs.environment)

(def article
  {:title "Test environment"
   :last-updated "Jul 23, 2013"
   :url :environment
   :content
   [:div
    [:p
     "Occasionally, bugs in tests arise because CircleCI's environment differs slightly from your local environment.In the future, we plan to allow as much of this to be configurable as possible.Please + $c(HAML['contact_us']())if some part of our environment is not suitable for you, and we will try to come up with a workaround."]
    [:p
     "If any version is not listed here, SSH into our build boxes to check it manually (and + $c(HAML['contact_us']())so we can update this doc."]
    [:h2#base "Base"]
    [:p
     "Our base image uses Ubuntu 12.04, with the addition of many packagescommonly used in web development.Some specifics:"]
    [:ul
     [:li [:code "Architecture: x86_64"]]
     [:li [:code "Username: ubuntu"]]
     [:li [:code "Ubuntu 12.04 (precise)"]]
     [:li [:code "Kernel version: 3.2"]]
     [:li [:code " + ($e($c(this.v.v('git')))) + "]]
     [:li [:code " + ($e($c(this.v.v('gcc')))) + "]]
     [:li [:code " + ($e($c(this.v.v('g++')))) + "]]
     [:li [:code "GNU make 3.81"]]]
    [:h2#env-vars "Environmental Variables"]
    [:p
     "See"
     [:a {:href "/docs/environment-variables"} "this doc"]
     "for a thorough list of all available environment variables. Here are some of the mostuseful ones:"]
    [:dl
     [:dt [:code "CIRCLECI=true"]]
     [:dt [:code "CI=true"]]
     [:dt [:code "DISPLAY=:99"]]
     [:dt [:code "CIRCLE_BRANCH"]]
     [:dd "The name of the branch being tested, such as 'master'"]
     [:dt [:code "CIRCLE_SHA1"]]
     [:dd "The SHA1 of the commit being tested"]
     [:dt [:code "CIRCLE_BUILD_NUM"]]
     [:dd "The build number, same as in circleci.com/gh/foo/bar/123"]
     [:dt [:code "CIRCLE_PROJECT_USERNAME"]]
     [:dd "The username of the github repo, 'foo' in github.com/foo/bar"]
     [:dt [:code "CIRCLE_PROJECT_REPONAME"]]
     [:dd
      "The repo name of the github repo, 'bar' in github.com/foo/bar"]
     [:dt [:code "CIRCLE_USERNAME"]]
     [:dd "The github login of the user who triggered the build"]]
    [:p
     "You can use the"
     [:code "CI"]
     "and"
     [:code "CIRCLECI"]
     "environment variables in your program, if you need to have CI-specific behavior in you application.Naturally, this is not recommended in principle, but it can occasionally be useful in practice."]
    [:h2#browsers "Browsers and GUIs"]
    [:p
     "CircleCI runs graphical programs in a virtual framebuffer, usingThis means programs like Selenium, Capybara, Jasmine, and other testing tools which require a browser will work perfectly, just like they do when you use them locally.You do not need to do anything special to set this up.We have"
     [:code "phantomjs 1.9.2"]
     ","
     [:code " + ($e($c(this.v.v('casperjs')))) + "]
     "and"
     [:code "libwebkit (2.2.1-1ubuntu4)"]
     "pre-installed,for Capybara and other headless browser interfaces."]
    [:p
     "Xvfb runs on port 99, and the appropriate"
     [:code "DISPLAY"]
     "environment variable has already been set."]
    [:p
     "Selenium-based tests are able to use Chrome stable channel + $e($c(( + (this.v.v('Chrome'))))with + $e($c(this.v.v('chromedriver')))as of October, 2013), andChromedriver 23.0 is also available as"
     [:code "chromedriver23"]]
    [:p
     [:span.label.label-info "Help"]
     [:a
      {:href "/docs/troubleshooting-browsers"}
      "Check out our browser debugging docs."]]
    [:h2 "Languages"]
    [:h3 "Ruby"]
    [:p
     "We use RVM to give you access to a wide variety of Rubyversions. Below are the versions of Ruby that we pre-install; you can specify versions not listed here (supported by RVM) in your circle.yml file and we will install them as part of the build - this will add to your build time, however, if you let us know the version you are using we will update the VM accordingly. "]
    [:p
     "You can"
     [:a {:href "/docs/configuration#ruby-version"}]
     "choose the exact version you need directly, from the following list:"]
    [:ul [:li [:code " + ($e($c(r))) + "]]]
    [:p
     "By default we use"
     [:code "’  Ruby   + $e($c(this.v.default_ruby))‘"]
     "unless we detect that you need Ruby 1.8.7, in which case we'll useThis is installed viaRVM (stable)."]
    [:p
     "We also have a number of Ruby commands pre-installed if you need to use them directly. They use Ruby"]
    [:ul
     [:li [:code " + ($e($c(this.v.v('bundler')))) + "]]
     [:li [:code " + ($e($c(this.v.v('cucumber')))) + "]]
     [:li [:code " + ($e($c(this.v.v('rspec')))) + "]]
     [:li [:code " + ($e($c(this.v.v('rake')))) + "]]]
    [:h3#nodejs "node.js"]
    [:p
     "We use NVM to provide access to a wide range of node versions.We currently have a small set of Node versions installed, but any version of Node that you specify in your"
     [:code "circle.yml"]
     "will install instantly, so it's easy to use any Node version."]
    [:p
     "Below are the versions of Node.js that we pre-install; you can specify versions not listed here (supported by NVM) in your circle.yml file and we will install them as part of the build - this will add to your build time, however, if you let us know the version you are using we will update the VM accordingly. "]
    [:ul [:li [:code " + ($e($c(n))) + "]]]
    [:p "If you do not specify a version, we use"]
    [:h3 "Python"]
    [:p
     "We useby default, although you canPackages can be installed using"
     [:code " + ($e($c(this.v.v('pip')))) + "]
     "and"]
    [:p
     "Below are the versions of Python that we pre-install; you can specify versions not listed here (supported by pyenv) in your circle.yml file and we will install them as part of the build - this will add to your build time, however, if you let us know the version you are using we will update the VM accordingly. "]
[:div.row [:div.span2 [:ul [:li [:code " + $e($c(v))"]]]]]
[:p
 "Please + $c(HAML['contact_us']())if other versions of Python would be useful to you."]
[:h3 "PHP"]
[:p
 "We useby default, although you canPackages can be installed using"
 [:code "composer"]
 ", "
 [:code "pear"]
 ", and "
 [:code "pecl"]
 "."]
[:p "Supported versions are:"]
[:div.row [:div.span2 [:ul [:li [:code " + $e($c(v))"]]]]]
[:p
 "Are you using a version of PHP that isn't included in this list?If so, please"]
[:h3#java "Java (and JVM based languages)"]
[:p "CircleCI has the following languages and tools installed:"]
[:ul
 [:li [:code "oracle JDK 7, using Java 1.7.0_21"] " (default)"]
 [:li [:code "oracle JDK 6, using Java 1.6.0u37"]]
 [:li [:code "openjdk7"]]
 [:li [:code "openjdk6"]]
 [:li [:code " + ($e($c(this.v.v('ant')))) + "]]
 [:li [:code " + ($e($c(this.v.v('maven')))) + "]]
 [:li [:code " + ($e($c(this.v.v('gradle')))) + "]]
 [:li [:code " + ($e($c(this.v.v('play')))) + "]]]
[:p
 "You can specify the following JVM versions in your "
 [:code "circle.yml"]
 " file:"]
[:ul
 [:li [:code "oraclejdk8"]]
 [:li [:code "oraclejdk7"] " (default)"]
 [:li [:code "oraclejdk6"]]
 [:li [:code "openjdk7"]]
 [:li [:code "openjdk6"]]]
[:h4 "Scala"]
"We track "
[:a
 "http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"]
" for recent Scala releases:"
[:ul [:li [:code " + ($e($c(s))) + "]]]
"We also install some release candidate and beta versions (see the above URL for the complete list)."
[:h3 "Clojure"]
[:p "We use" [:code " + ($e($c(this.v.v('lein')))) + "]]
[:p
 "You should specify your Clojure version in your"
 [:code "project.clj"]
 "file."]
[:p
 "Other JVM-based languages should also work. please + $c(HAML['contact_us']())let us know if you need anything else installed to run your JVM language of choice."]
[:h3 "Haskell"]
[:p "We have the following tools installed:"]
[:ul
 [:li [:code " + ($e($c('ghc ' + v))) + "]]
 [:li [:code " + ($e($c('cabal-install-1.18'))) + "]]
 [:li [:code " + ($e($c('happy-1.19.3'))) + "]]
 [:li [:code " + ($e($c('alex-3.1.3'))) + "]]]
[:p
 "You can"
 [:a
  {:href "/docs/configuration#ghc-version"}
  "specify which GHC version"]
 "you'd like in your "
 [:code "circle.yml"]
 "."]
[:h3#other "Other languages"]
[:p
 "We currently have a number of packages installed to help you test your backend applications, including:"]
[:ul
 [:li [:code " + ($e($c(this.v.v('gcc')))) + "]]
 [:li [:code " + ($e($c(this.v.v('g++')))) + "]]
 [:li [:code " + ($e($c(this.v.v('golang')))) + "]]
 [:li [:code " + ($e($c(this.v.v('erlang')))) + "]]]
[:h2#databases "Databases and Services"]
[:p
 "We have the following services automatically set up for your tests:"]
[:ul
 [:li
  [:code " + ($e($c(this.v.v('postgresql')))) + "]
  "(including postgis 2.0 extensions)"]
 [:li [:code " + ($e($c(this.v.v('mysql')))) + "]]
 [:li [:code " + ($e($c(this.v.v('mongodb')))) + "]]
 [:li [:code " + ($e($c(this.v.v('riak')))) + "]]
 [:li [:code " + ($e($c(this.v.v('cassandra')))) + "]]
 [:li [:code " + ($e($c(this.v.v('redis')))) + "]]
 [:li [:code " + ($e($c(this.v.v('memcache')))) + "]]
 [:li [:code " + ($e($c(this.v.v('sphinx')))) + "]]
 [:li [:code " + ($e($c(this.v.v('elasticsearch')))) + "]]
 [:li [:code " + ($e($c(this.v.v('solr')))) + "]]
 [:li [:code " + ($e($c(this.v.v('beanstalkd')))) + "]]
 [:li [:code " + ($e($c(this.v.v('couchbase')))) + "]]
 [:li [:code " + ($e($c(this.v.v('couchdb')))) + "]]
 [:li [:code " + ($e($c(this.v.v('neo4j')))) + "]]
 [:li [:code " + ($e($c(this.v.v('rabbitmq')))) + "]]]
[:p
 "Both"
 [:code "postgres"]
 "and"
 [:code "mysql"]
 "are set up to use the"
 [:code "ubuntu"]
 "user, have a database called"
 [:code "circle_test"]
 "available, and don't require any password.The other databases should not need any specific username or password, and should just work."]
[:p
 "Several services are disabled by default because they're notcommonly used, or because of memory requirements. We try todetect and enable them automatically, but in casewe fail (or don't have inference in your language), you canenable them by adding to your circle.yml:"]
[:pre
 "’‘"
 [:code.no-highlight "’machine:  services:    \\- cassandra‘"]
 "’‘"]
[:p "The list of services that can be enabled this way is"]
[:ul
 [:li [:code "cassandra"]]
 [:li [:code "elasticsearch"]]
 [:li [:code "rabbitmq-server"]]
 [:li [:code "riak"]]
 [:li [:code "beanstalkd"]]
 [:li [:code "couchbase-server"]]
 [:li [:code "neo4j"]]]]})
