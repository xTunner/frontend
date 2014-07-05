(ns frontend.components.documentation
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.docs :as docs]
            [frontend.components.docs.how-parallelism-works :as how-parallelism-works]
            [frontend.components.docs.parallel-manual-setup :as manual-parallelism]
            [frontend.components.docs.getting-started :as getting-started]
            [frontend.components.docs.manually :as manually]
            [frontend.components.docs.unusual :as unusual]
            [frontend.components.docs.configuration :as configuration]
            [frontend.components.docs.ssh-build :as ssh-build]
            [frontend.components.docs.troubleshooting-browsers :as troubleshooting-browsers]
            [frontend.components.docs.troubleshooting-ruby :as troubleshooting-ruby]
            [frontend.components.docs.troubleshooting-python :as troubleshooting-python]
            [frontend.components.docs.troubleshooting-nodejs :as troubleshooting-nodejs]
            [frontend.components.docs.troubleshooting-php :as troubleshooting-php]
            [frontend.components.docs.troubleshooting-clojure :as troubleshooting-clojure]
            ;; [frontend.components.docs.troubleshooting-haskell :as troubleshooting-haskell]
            [frontend.components.docs.oom :as oom]
            [frontend.components.docs.file-ordering :as file-ordering]
            [frontend.components.docs.filesystem :as filesystem]
            [frontend.components.docs.time-date :as time-date]
            [frontend.utils :refer [mlog]]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]))

(def documentation-pages
  {:ssh-build ssh-build/article
   :troubleshooting-browsers troubleshooting-browsers/article
   :troubleshooting-ruby troubleshooting-ruby/article
   :troubleshooting-python troubleshooting-python/article
   :troubleshooting-nodejs troubleshooting-nodejs/article
   :troubleshooting-php troubleshooting-php/article
   :troubleshooting-clojure troubleshooting-clojure/article
   :how-parallelism-works how-parallelism-works/article
   :manual-parallelism manual-parallelism/article
   })

(def sidebar-menu
  [{:name "Getting Started"
    :ref :getting-started
    :docs []}
   {:name "Languages"
    :ref :languages
    :docs []}
   {:name "How-to"
    :ref :how-to
    :docs []}
   {:name "Troubleshooting"
    :ref :troubleshooting
    :docs []}
   {:name "Reference"
    :ref :reference
    :docs []}
   {:name "Parallelism"
    :ref :parallelism
    :docs [:how-parallelism-works
           :manual-parallelism]}
   {:name "Privacy and Security"
    :ref :privacy-and-security
    :docs []}])

(defn sidebar-article [article-ref]
  (let [article (get documentation-pages article-ref)
        article-name (:url article)
        article-href (str "/docs/" (name article-name))]
    [:li
     [:a {:href article-href} (:title article)]]))

(defn sidebar-category [category]
  [:ul.articles
   [:li
    [:h4
     [:a {:href (:ref category)} (:name category)]]]
   (map #(sidebar-article %) (:docs category))])

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
             [:p.meta
              [:strong "Last Updated "]
              (:last-updated article)]
             (:content article)]))))

(defrender documentation [app owner opts]
  (mlog "App state " app)
  (html (doc-section
          [:article
           [:h1 "What can we help you with?"]
           (doc-search)
           [:div.row
            [:h4 "Having problems? Check these sections"]
            [:div.articles.span4
             [:h4 "Getting started"]
             [:ul.articles_list
              (map #(sidebar-article %)
                   [#_getting-started/article
                    #_manually/article
                    #_unusual/article
                    #_configuration/article])]]
            [:div.articles.span4
             [:h4 "Troubleshooting"]
             [:ul.articles_list
              (map #(sidebar-article %)
                   [:ssh-build
                    :troubleshooting-browsers
                    :troubleshooting-ruby
                    :troubleshooting-python
                    :troubleshooting-nodejs
                    :troubleshooting-php
                    :troubleshooting-clojure
                    ;; troubleshooting-haskell/article
                    ;; oom/article
                    ;; file-ordering/article
                    ;; filesystem/article
                    ;; time-date/article
                    ])]]]])))

