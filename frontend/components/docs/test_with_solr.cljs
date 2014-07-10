(ns frontend.components.docs.test-with-solr)

(def article
  {:title "Test with Solr"
   :last-updated "Jul 23, 2013"
   :url :test-with-solr
   :content [:div
             [:p
              "If your tests require a running solr instance, you will need to configureand boot solr before they run in CircleCI."]
             [:h2 "The Easy Way"]
             [:p
              "In some cases, we can start solr automatically. In particular, if you'reusing ruby and the sunspot_solr gem, we'll run"
              [:code "rake sunspot:solr:start"]
              "by default, and it should Just Work."]
             [:p
              "If you're using a library or module with similar functionality (i.e. onethat provides a bundled solr, and a wrapper for booting it), please + $c(HAML['contact_us']())so that we can extend our inference to make it work automatically!"]
             [:h2 "The Hard Way"]
             [:p
              "Even if we aren't able to do things automatically,"
              [:code "solr + $e($c(CI.Versions.solr))"]
              "is installed on your build system. It will need to be configured with yourschema.xml, and booted viaHere's an example of how to do so:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’database:  post:    - cp -R /opt/solr-4.3.1 $HOME/solr    - cp config/schema.xml $HOME/solr/example/solr/collection1/conf    # optional: - cp config/solrconfig.xml $HOME/solr/example/solr/collection1/conf    - cd $HOME/solr/example; java -jar start.jar $HOME/solr.log:        background: true‘"]
              "’‘"]
             [:p
              "This configuration does three things. You may need to fine-tune the exact commandsto match your needs, but they should:"]
             [:ol
              [:li
               "Copy a skeletal solr installation from"
               [:code "/opt/solr-4.3.1"]
               "into your home directory."]
              [:li
               "Copy your configuration ("
               [:code "schema.xml"]
               " at least, and"
               [:code "solrconfig.xml"]
               "if you need it)into place."]
              [:li "Launch solr as a"]]
             [:p
              "Solr, when started this way, will be running under"
              [:code "http://localhost:8983/solr/"]
              ",and logging to "
              [:code "$HOME/solr.log"]
              "."]
             [:p
              "Please + $c(HAML['contact_us']())and let us know if you're using solr this way! Your feedback helps us keep ourdocumentation up to date, and our services as useable as possible."]]})

