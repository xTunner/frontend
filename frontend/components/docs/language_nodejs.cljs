(ns frontend.components.docs.language-nodejs)

(def article
  {:title "Continuous Integration and Continuous Deployment with Node.js"
   :short-title "Node.js"
   :last-updated "March 12, 2014"
   :url :language-nodejs
   :content [:div
             [:p
              "Circle has great support for Node.js applications.We inspect your code before each build to infer your settings, dependencies, and test steps."]
             [:p
              "If your project has any special requirements, you can augment or override ourinferred commands from a"
              [:a {:href "/docs/configuration"} "circle.yml"]
              "file checked into your repo's root directory. You can also add"
              [:a {:href "/docs/configuration#deployment"} "deployment"]
              "commands that will run after a green build."]
             [:h3 "Version"]
             [:p
              "Circle has"
              [:a {:href "/docs/environment#nodejs"} "several Node versions"]
              "pre-installed.We use"
              [:code " + ($e($c(CI.Versions.default_node))) + "]
              "as our default version. If you'd like a specific version, then you can specify it in your circle.yml:"]
             [:h3 "Dependencies"]
             [:p
              "If Circle finds a "
              [:code "package.json"]
              ",we automatically run "
              [:code "npm install"]
              " to fetchall of your project's dependencies.If needed, you can add custom dependencies commands from your circle.yml.For example, you can override our default command to pass a special flag to "
              [:code "npm"]
              ":"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  override:    - npm install --dev‘"]
              "’‘"]
             [:h3 "Databases"]
             [:p
              "We have pre-installed more than a dozenincluding PostgreSQL and MySQL. If needed, you can"
              [:a
               {:href "/docs/manually#dependencies"}
               "manually set up your test database"]
              "from your circle.yml."]
             [:h3 "Testing"]
             [:p
              "Circle will run "
              [:code "npm test"]
              " when youspecify a test script in "
              [:code "package.json"]
              ".We also run your Mocha tests as well as run any"
              [:code "test"]
              " targets in Cakefiles or Makefiles."]
             [:p
              "You can"
              [:a
               {:href "/docs/configuration#test"}
               "add additional test commands"]
              "from your circle.yml. For example, you could run a custom"
              [:code "test.sh"]
              "script:"]
             [:pre "’‘" [:code.no-highlight "’test:  post:    - ./test.sh‘"] "’‘"]
             [:h3 "Deployment"]
             [:p
              "Circle offers first-class support forWhen a build is green, Circle will deploy your project as directedin your "
              [:code "circle.yml"]
              " file.We can deploy to Nodejitsu and other PaaSes as well as tophysical servers under your control."]
             [:p
              "If you have any trouble, please + $c(HAML.contact_us())and we will be happy to help."]]})
