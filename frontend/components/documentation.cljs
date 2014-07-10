(ns frontend.components.documentation
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.docs :as docs]
            [frontend.components.docs.android :as android]
            [frontend.components.docs.api :as api]
            [frontend.components.docs.background-process :as background-process]
            [frontend.components.docs.browser-debugging :as browser-debugging]
            [frontend.components.docs.build-artifacts :as build-artifacts]
            [frontend.components.docs.bundler-latest :as bundler-latest]
            [frontend.components.docs.cant-follow :as cant-follow]
            [frontend.components.docs.capybara-timeout :as capybara-timeout]
            [frontend.components.docs.chromedriver-moving-elements :as chromedriver-moving-elements]
            [frontend.components.docs.clojure-12 :as clojure-12]
            [frontend.components.docs.composer-api-rate-limit :as composer-api-rate-limit]
            [frontend.components.docs.config-sample :as config-sample]
            [frontend.components.docs.configuration :as configuration]
            [frontend.components.docs.continuous-deployment :as continuous-deployment]
            [frontend.components.docs.deploy-google-app-engine :as deploy-google-app-engine]
            [frontend.components.docs.dont-run :as dont-run]
            [frontend.components.docs.ec2ip-and-security-group :as ec2ip-and-security-group]
            [frontend.components.docs.environment :as environment]
            [frontend.components.docs.environment-variables :as environment-variables]
            [frontend.components.docs.external-resources :as external-resources]
            [frontend.components.docs.file-ordering :as file-ordering]
            [frontend.components.docs.filesystem :as filesystem]
            [frontend.components.docs.getting-started :as getting-started]
            [frontend.components.docs.git-bundle-install :as git-bundle-install]
            [frontend.components.docs.git-npm-install :as git-npm-install]
            [frontend.components.docs.git-pip-install :as git-pip-install]
            [frontend.components.docs.github-permissions :as github-permissions]
            [frontend.components.docs.github-privacy :as github-privacy]
            [frontend.components.docs.github-security-ssh-keys :as github-security-ssh-keys]
            [frontend.components.docs.how-parallelism-works :as how-parallelism-works]
            [frontend.components.docs.how-to :as how-to]
            [frontend.components.docs.installing-custom-software :as installing-custom-software]
            [frontend.components.docs.installing-elasticsearch :as installing-elasticsearch]
            [frontend.components.docs.introduction-to-continuous-deployment :as introduction-to-continuous-deployment]
            [frontend.components.docs.language-haskell :as language-haskell]
            [frontend.components.docs.language-nodejs :as language-nodejs]
            [frontend.components.docs.language-php :as language-php]
            [frontend.components.docs.language-python :as language-python]
            [frontend.components.docs.language-ruby-on-rails :as language-ruby-on-rails]
            [frontend.components.docs.languages :as languages]
            [frontend.components.docs.look-at-code :as look-at-code]
            [frontend.components.docs.manually :as manually]
            [frontend.components.docs.missing-dir :as missing-dir]
            [frontend.components.docs.missing-file :as missing-file]
            [frontend.components.docs.oom :as oom]
            [frontend.components.docs.parallel-manual-setup :as parallel-manual-setup]
            [frontend.components.docs.permissions-and-access-during-deployment :as permissions-and-access-during-deployment]
            [frontend.components.docs.php-memcached-compile-error :as php-memcached-compile-error]
            [frontend.components.docs.polling-project-status :as polling-project-status]
            [frontend.components.docs.privacy-security :as privacy-security]
            [frontend.components.docs.reference :as reference]
            [frontend.components.docs.requires-admin :as requires-admin]
            [frontend.components.docs.rspec-wrong-exit-code :as rspec-wrong-exit-code]
            [frontend.components.docs.ruby-debugger-problems :as ruby-debugger-problems]
            [frontend.components.docs.ruby-exception-during-schema-load :as ruby-exception-during-schema-load]
            [frontend.components.docs.skip-a-build :as skip-a-build]
            [frontend.components.docs.ssh-build :as ssh-build]
            [frontend.components.docs.status-badges :as status-badges]
            [frontend.components.docs.test-with-solr :as test-with-solr]
            [frontend.components.docs.time-date :as time-date]
            [frontend.components.docs.time-day :as time-day]
            [frontend.components.docs.time-seconds :as time-seconds]
            [frontend.components.docs.troubleshooting :as troubleshooting]
            [frontend.components.docs.troubleshooting-browsers :as troubleshooting-browsers]
            [frontend.components.docs.troubleshooting-clojure :as troubleshooting-clojure]
            [frontend.components.docs.troubleshooting-nodejs :as troubleshooting-nodejs]
            [frontend.components.docs.troubleshooting-php :as troubleshooting-php]
            [frontend.components.docs.troubleshooting-python :as troubleshooting-python]
            [frontend.components.docs.troubleshooting-ruby :as troubleshooting-ruby]
            [frontend.components.docs.unusual :as unusual]
            [frontend.components.docs.what-happens :as what-happens]
            [frontend.components.docs.wrong-ruby-commands :as wrong-ruby-commands]
            [frontend.components.docs.wrong-ruby-version :as wrong-ruby-version]
            [frontend.utils :refer [mlog]]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]))

(defn docmap
  [docs]
  (zipmap (map :url docs) docs))

(def documentation-pages
  (docmap [android/article
           api/article
           background-process/article
           browser-debugging/article
           build-artifacts/article
           bundler-latest/article
           cant-follow/article
           capybara-timeout/article
           chromedriver-moving-elements/article
           clojure-12/article
           composer-api-rate-limit/article
           config-sample/article
           configuration/article
           continuous-deployment/article
           deploy-google-app-engine/article
           dont-run/article
           ec2ip-and-security-group/article
           environment/article
           environment-variables/article
           external-resources/article
           file-ordering/article
           filesystem/article
           getting-started/article
           git-bundle-install/article
           git-npm-install/article
           git-pip-install/article
           github-permissions/article
           github-privacy/article
           github-security-ssh-keys/article
           how-parallelism-works/article
           how-to/article
           installing-custom-software/article
           installing-elasticsearch/article
           introduction-to-continuous-deployment/article
           language-haskell/article
           language-nodejs/article
           language-php/article
           language-python/article
           language-ruby-on-rails/article
           languages/article
           look-at-code/article
           manually/article
           missing-dir/article
           missing-file/article
           oom/article
           parallel-manual-setup/article
           permissions-and-access-during-deployment/article
           php-memcached-compile-error/article
           polling-project-status/article
           privacy-security/article
           reference/article
           requires-admin/article
           rspec-wrong-exit-code/article
           ruby-debugger-problems/article
           ruby-exception-during-schema-load/article
           skip-a-build/article
           ssh-build/article
           status-badges/article
           test-with-solr/article
           time-date/article
           time-day/article
           time-seconds/article
           troubleshooting/article
           troubleshooting-browsers/article
           troubleshooting-clojure/article
           troubleshooting-nodejs/article
           troubleshooting-php/article
           troubleshooting-python/article
           troubleshooting-ruby/article
           unusual/article
           what-happens/article
           wrong-ruby-commands/article
           wrong-ruby-version/article]))

(def getting-started
  [:getting-started
   :manually
   :unusual
   :configuration])

(def how-to
  [:installing-custom-software
   :background-process
   :code-coverage
   :external-resources
   :dont-run
   :skip-a-build
   :nightly-builds
   :test-with-solr
   :android
   :continuous-deployment-with-heroku
   :deploy-google-app-engine])

(def languages
  [:language-ruby-on-rails
   :language-python
   :language-nodejs
   :language-php
   :language-java
   :language-haskell])

(def troubleshooting
  [:ssh-build
   :troubleshooting-browsers
   :troubleshooting-ruby
   :troubleshooting-python
   :troubleshooting-nodejs
   :troubleshooting-php
   :troubleshooting-clojure
   :troubleshooting-haskell
   :oom
   :file-ordering
   :filesystem
   :time-date])

(def reference
  [:configuration
   :config-sample
   :environment
   :environment-variables
   :permissions-and-access-during-deployment
   :status-badges
   :polling-project-status
   :api
   :build-artifacts
   :ec2ip-and-security-group])

(def parallelism
  [:how-parallelism-works
   :parallel-manual-setup])

(def privacy-and-security
  [:github-privacy
   :look-at-code])

(def sidebar-menu
  [{:name "Getting Started"
    :ref :getting-started
    :docs getting-started}
   {:name "Languages"
    :ref :languages
    :docs languages}
   {:name "How-to"
    :ref :how-to
    :docs how-to}
   {:name "Troubleshooting"
    :ref :troubleshooting
    :docs troubleshooting}
   {:name "Reference"
    :ref :reference
    :docs reference}
   {:name "Parallelism"
    :ref :parallelism
    :docs parallelism}
   {:name "Privacy and Security"
    :ref :privacy-and-security
    :docs privacy-and-security}])

(defn article-list-item [article-ref & [title-key]]
  (if-let [article (get documentation-pages article-ref)]
    (let [article-name (:url article)
          article-href (str "/docs/" (name article-name))
          article-children (:children article)
          title (or
                  ((or title-key :title) article)
                  ;; if no short title exists, fall back to using :title
                  (:title article))
          formatted-title (if (seq article-children)
                            (str title " (" (count article-children) ")")
                            title)]
      [:li
       [:a {:href article-href} formatted-title]])
    (mlog "No article found for " article-ref)))

(defn sidebar-category [category]
  [:ul.articles
   [:li
    [:h4
     [:a {:href (:ref category)} (:name category)]]]
   (map #(article-list-item % :short-title) (:docs category))])

(def sidebar (map #(sidebar-category %) sidebar-menu))

(defn doc-section
  [inner] 
  [:div.docs.page
   [:div.banner
    [:div.container
     [:h1 "Documentation"]]]
   [:div.container.content
    [:div.row
     [:aside.span3 sidebar]
     [:div.span8.offset1
      [:article
       inner]]]]])

(defn doc-search
  []
  [:form.clearfix.form-search#searchDocs
   [:input#searchQuery {:name "query"
                        :placeholder "What can we help you find?"
                        :type "search"}]
   [:button {:type "submit"}
    [:i.fa.fa-search]]])


(defrender docpage [app owner opts]
  (let [article (-> app :current-documentation-page keyword documentation-pages)]
    (html (doc-section
            [:article
             (doc-search)
             [:h1 (:title article)]
             (when-let [last-updated (:last-updated article)]
               [:p.meta
                [:strong "Last Updated "]
                last-updated])
             (when-let [children (-> article :children seq)]
               [:ul.article_list
                (map article-list-item children)])
             (:content article)]))))

(defrender documentation [app owner opts]
  (html (doc-section
          [:article
           [:h1 "What can we help you with?"]
           (doc-search)
           [:div.row
            [:h4 "Having problems? Check these sections"]
            [:div.articles.span4
             [:h4 "Getting started"]
             [:ul.articles_list
              (map article-list-item getting-started)]]
            [:div.articles.span4
             [:h4 "Troubleshooting"]
             [:ul.articles_list
              (map article-list-item troubleshooting)]]]])))

