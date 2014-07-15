(ns frontend.components.docs.language-php)

(def article
  {:title "Continuous Integration and Continuous Deployment with PHP"
   :short-title "PHP"
   :last-updated "March 12, 2014"
   :url :language-php
   :content [:div
             [:p
              "Circle works seamlessly with all the tools and package managers that help PHP developers test and deploy their code. We can generally infer most of your dependencies and test commands, but we also provide custom configuration via a"
              [:code "circle.yml"]
              "checked into your repo's root directory."]
             [:h3 "Version"]
             [:p
              "Circle supports more than a dozen"
              [:a {:href "/docs/environment#php"} "versions of PHP,"]
              "and uses"
              [:code " + ($e($c(CI.Versions.default_php))) + "]
              "as the default. You can set a custom version of PHP in the machine section of your circle.yml:"]
             [:p
              "Please + $c(HAML.contact_us())if you need a different version; we'll be happy to install it for you."]
             [:h3 "Dependencies"]
             [:p
              "Circle has the composer, pear, and pecl package managers installed.If we find a composer.json file, then we'll automatically run "
              [:code "composer install"]
              "."]
             [:p
              "To install your dependencies with either "
              [:code "pear"]
              " or "
              [:code "pecl"]
              ",you have to include"
              [:a
               {:href "/docs/configuration#dependencies"}
               "dependency commands"]
              "in your "
              [:code "circle.yml"]
              " file.The following example shows how to install the MongoDB extension using "
              [:code "pecl"]
              "."]
             [:pre
              "’‘"
              [:code.no-highlight "’dependencies:  pre:    - pecl install mongo‘"]
              "’‘"]
             [:p
              "You can also edit your PHP configuration from your "
              [:code "circle.yml"]
              ". For example, if you have a custom configuration file checked in to your repo, then you could do:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  pre:    - cp config/custom.ini ~/.phpenv/versions/$(phpenv global)/etc/conf.d/‘"]
              "’‘"]
             [:p
              [:span.label.label-info "Note:"]
              [:code "phpenv global"]
              " returns the PHP version that has beenspecified in your "
              [:code "circle.yml"]
              " file."]
             [:p
              "Here's another example showing how you could adjust PHP settings ina "
              [:code ".ini"]
              " file."]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  pre:    - echo \\memory_limit = 64M\\ > ~/.phpenv/versions/$(phpenv global)/etc/conf.d/memory.ini‘"]
              "’‘"]
             [:p
              [:span.label.label-info "Note:"]
              "you'll have to specify your PHP version in your "
              [:code "circle.yml"]
              " in order to edit PHP's configuration files."]
             [:h3 "Databases"]
             [:p
              "We have pre-installed more than a dozenincluding PostgreSQL and MySQL.If needed, you have the option of"]
             [:h3#php-apache "Using the Apache Webserver"]
             [:p
              "Apache2 is already installed on CircleCI containers but it needs to beconfigured to host your PHP application."]
             [:p
              "To enable your site check a file containing your site configuration into yourrepository and copy it to "
              [:code "/etc/apache2/sites-available/"]
              " duringyour build.Then enable the site with "
              [:code "a2ensite"]
              " and restart Apache."]
             [:p
              "An example configuration that sets up Apache to serve the PHP site from"
              [:code "/home/ubuntu/MY-PROJECT/server-root"]
              " is:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’Listen 8080<VirtualHost *:8080>  LoadModule php5_module /home/ubuntu/.phpenv/versions/PHP_VERSION/libexec/libphp5.so  DocumentRoot /home/ubuntu/MY-PROJECT/server-root  ServerName host.example.com  <FilesMatch \\\\.php$>    SetHandler application/x-httpd-php  </FilesMatch></VirtualHost>‘"]
              "’‘"]
             [:p
              "Replace "
              [:code "MY-SITE"]
              " in with the name of your site configurationfile and "
              [:code "PHP_VERSION"]
              " with the version of PHP you configuredin your "
              [:code "circle.yml"]
              "."]
             [:p
              "Then enable your site and restart Apache by adding the following to your "
              [:code "circle.yml"]]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  post:    - cp ~/MY-PROJECT/MY-SITE /etc/apache2/sites-available    - a2ensite MY-SITE    - sudo service apache2 restart‘"]
              "’‘"]
[:h3 "Testing"]
[:p
 "Circle always runs your tests on a fresh machine. If we find a "
 [:code "phpunit.xml"]
 " file in your repo, then we'll run "
 [:code "phpunit"]
 " for you. You can add custom test commands to the test section of your "
 [:code "circle.yml"]
 ":"]
[:pre
 "’‘"
 [:code.no-highlight
  "’test:  override:    - ./my_testing_script.sh‘"]
 "’‘"]
[:h3#xdebug "Enable Xdebug"]
[:p
 "Xdebug is installed for all versions of PHP, but is disabled (for performance reasons) bydefault. It is simple to enable this tool by adding the following to your"
 [:code "circle.yml"]
 " file:"]
[:pre
 "’‘"
 [:code.no-highlight
  "’dependencies:  pre:    - sed -i 's/^;//' ~/.phpenv/versions/$(phpenv global)/etc/conf.d/xdebug.ini‘"]
 "’‘"]
[:p]
[:h3 "Deployment"]
[:p
 "Circle offers first-class support forWhen a build is green, Circle will deploy your project as directedin your "
 [:code "circle.yml"]
 " file.We can deploy to PaaS providers as well as tophysical servers under your control."]
[:h3 "Troubleshooting for PHP"]
[:p
 "If you run into problems, check out our"
 [:a {:href "/docs/troubleshooting-php"} "PHP troubleshooting"]
 "write-ups about these issues:"]
" + $c(this.include_article('troubleshooting_php'))"
[:p
 "If you are still having trouble, please + $c(HAML.contact_us())and we will be happy to help."]]})
