(ns frontend.components.docs.language-python)

(def article
  {:title "Continuous Integration and Continuous Deployment with Python"
   :short-title "Python"
   :last-updated "March 12, 2014"
   :url :language-python
   :content [:div
             [:p
              "CircleCI works well for Python projects. We run automatic inference on each build to determine your dependencies and test commands. If we don't infer all of your settings, you can also add custom configuration to a "
              [:a {:href "/docs/configuration"} "circle.yml"]
              " file checked into your repo's root directory."]
             [:h3 "Version"]
             [:p
              "When Circle detects Python, we automatically use "
              [:code "virtualenv"]
              " to create an isolated Python environment using Python"]
             [:p
              "We have "
              [:a {:href "/docs/environment#python"} "many versions of Python"]
              " pre-installed. If you don't want to use the default, you can specify your Python version from your circle.yml:"]
             [:p
              "Please + $c(HAML.contact_us())if other versions of Python would be of use to you."]
             [:p
              [:span.label.label-info "Note:"]
              " Circle will set up "
              [:code "virtualenv"]
              " if you specify your Python version in your "
              [:code "circle.yml"]
              ". This can be useful if we didn't automatically detect that you're using Python."]
             [:h3 "Package managers and dependencies"]
             [:p
              "Circle automatically installs your dependencies using either "
              [:code "pip"]
              " when we find a "
              [:code "requirements.txt"]
              ", or "
              [:code "distutils"]
              " when we find a "
              [:code "setup.py"]
              " file."]
             [:p
              "You can also "
              [:a
               {:href "/docs/configuration#dependencies"}
               " add custom dependencies commands"]
              "from your "
              [:code "circle.yml"]
              ", for example:"]
             [:pre
              [:code.no-highlight
               "dependencies:\n  pre:\n    - pip install PIL --allow-external PIL --allow-unverified PIL"]]
             [:h3 "Databases"]
             [:p
              "Circle has pre-installed more than a dozenincluding PostgreSQL and MySQL. If needed, you can "
              [:a
               {:href "/docs/manually#dependencies"}
               "manually set up your test database"]
              " from your "
              [:code "circle.yml"]
              "."]
             [:h3 "Testing"]
             [:p
              "CircleCI automatically runs "
              [:code "tox"]
              " when we find a "
              [:code "tox.ini"]
              " file, and runs "
              [:code "nosetests"]
              " when we find a "
              [:code "unittest.py"]
              " file. If you are using Django, then Circle will run "
              [:code "manage.py test"]
              ". You can"
              [:a {:href "/docs/configuration#test"} "add custom test commands"]
              " from your "
              [:code "circle.yml"]
              ":"]
             [:pre
              [:code.no-highlight
               "test:\n  override:\n    - ./my_testing_script.sh"]]
             [:h3 "Deployment"]
             [:p
              "CircleCI has "
              [:a
               {:href "/docs/configuration#deployment"}
               "first-class support for deployment"]
              "with Fabric or Paver.To set up deployment after green builds, you can add commands to the deployment section of your "
              [:code "circle.yml"]
              ":"]
             [:pre
              [:code.no-highlight
               "deployment:\n  production:\n    branch: master\n    commands:\n      - fab deploy"]]
             [:h3 "Troubleshooting for Python"]
             [:p
              "Problems? Check out our "
              [:a {:href "/docs/troubleshooting-python"} "Python troubleshooting"]
              " information:"]
             " + $c(this.include_article('troubleshooting_python'))"
             [:p
              "If you are still having trouble, please $c(HAML.contact_us()) and we will be happy to help."]]})

