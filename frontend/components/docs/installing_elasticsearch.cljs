(ns frontend.components.docs.installing-elasticsearch)

(def article
  {:title "Install a custom version of Elasticsearch"
   :last-updated "March 10, 2014"
   :url :installing-elasticsearch
   :content [:div
             [:p
              "CircleCI supports a large number of"
              [:a {:href "\\/docs/environment#databases\\"} "services"]
              " which can be enabled from a circle.yml file checked into your repo's root directory. To enable Elasticsearch, add the following to your circle.yml:"]
             [:pre "’‘" [:code "’machine:  services:    - elasticsearch‘"] "’‘"]
             [:p
              "The default version of elasticsearch isIf you need a custom version, you can download and start it from your build. To install 1.0.1, add the following to your circle.yml:"]
             [:pre
              "’  ‘"
              [:code
               "’  dependencies:  post:    - wget https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-1.0.1.tar.gz    - tar -xvf elasticsearch-1.0.1.tar.gz    - elasticsearch-1.0.1/bin/elasticsearch: {background: true}  ‘"]
              "’‘"]
             [:span.label.label-info "Note:"]
             "remember to remove elasticsearch from machine.services if you install it manually."
             [:h3 "Install an Elasticsearch plugin"]
             "It's easy to install a plugin from a url, just add a command to install the plugin before you start elasticsearch:"
             [:p]
             [:pre
              "’  ‘"
              [:code
               "’  dependencies:  post:    - wget https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-1.0.1.tar.gz    - tar -xvf elasticsearch-1.0.1.tar.gz    - elasticsearch-1.0.1/bin/plugin --url https://example.com/plugin.zip --install example-plugin    - elasticsearch-1.0.1/bin/elasticsearch: {background: true}  ‘"]
              "’‘"]
             [:h3 "Caching"]
             [:p
              "Downloading Elasticsearch can take time, making your build longer.To reduce the time spent installing dependencies, CircleCI can cache them between builds.You can add arbitrary directories to the cache, allowing you to avoid the overhead of building your custom software during the build."]
             [:p
              "Tell CircleCI to save a cached copy using theThen check for the directory before you download elasticsearch:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  cache_directories:    - elasticsearch-1.0.1 # relative to the build directory  post:    - if [[ ! -e elasticsearch-1.0.1 ]]; then wget https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-1.0.1.tar.gz && tar -xvf elasticsearch-1.0.1.tar.gz; fi    - elasticsearch-1.0.1/bin/elasticsearch: {background: true}‘"]
              "’‘"]]})
