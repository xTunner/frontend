(ns frontend.components.documentation
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [clojure.string :as string]
            [dommy.core :as dommy]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.docs :as doc-utils]
            [goog.string :as gstring])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [dommy.macros :refer [sel sel1]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn docs-search [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])]
        (utils/typeahead
         "#searchQuery"
         {:source (fn [query process]
                    (go (let [res (<! (ajax/managed-ajax :get "/autocomplete-articles"
                                                         :params {:query query}))]
                          (when (= :success (:status res))
                            (process (->> res
                                          :resp
                                          :suggestions
                                          (map gstring/htmlEscape)
                                          clj->js))))))
          :updater (fn [query]
                     (put! controls-ch [:edited-input {:path state/docs-search-path
                                                       :value query}])
                     (put! controls-ch [:doc-search-submitted {:query query}])
                     query)})))
    om/IRender
    (render [_]
      (let [query (get-in app state/docs-search-path)
            controls-ch (om/get-shared owner [:comms :controls])]
        (html
         [:form#searchDocs.clearfix.form-search
          [:input#searchQuery
           {:type "text",
            :value query
            :on-change #(utils/edit-input controls-ch state/docs-search-path %)
            :placeholder "What can we help you find?",
            :name "query"}]
          [:button {:on-click #(do (put! controls-ch [:doc-search-submitted {:query query}])
                                   false)
                    :type "submit"}
           [:i.fa.fa-search]]])))))

(defrender front-page [app owner]
  (let [query-results (get-in app state/docs-articles-results-path)
        query (get-in app state/docs-articles-results-query-path)
        docs (doc-utils/find-all-docs)]
    (html
     [:div
      [:h1 "What can we help you with?"]
      (om/build docs-search app)
      (when query-results
        [:div.article_list
         (if (empty? query-results)
           [:p "No articles found matching \"" [:strong query] "\""]
           [:div
            [:h5 "Articles matching \"" query "\""]
            [:ul.query_results
             (for [result query-results]
               [:li [:a {:href (:url result)} (:title result)]])]])])
      [:div.row
       [:h4 "Having problems? Check these sections"]
       [:ul.articles.span4
        [:h4 "Getting started"]
        (doc-utils/render-haml-template "article_list" {:article (:gettingstarted docs) :slug true})]
       [:ul.articles.span4
        [:h4 "Troubleshooting"]
        (doc-utils/render-haml-template "article_list" {:article (:troubleshooting docs) :slug true})]]])))

(defn add-link-targets [node]
  (doseq [heading (sel node
                       ".content h2, .content h3, .content h4, .content h5, .content h6")]
    (let [title (dommy/text heading)
          id (if-not (string/blank? (.-id heading))
               (.-id heading)
               (-> title
                   string/lower-case
                   string/trim
                   (string/replace \' "")     ; heroku's -> herokus
                   (string/replace #"[^a-z0-9]+" "-") ; dashes
                   (string/replace #"^-" "") ; don't let first or last be dashes
                   (string/replace #"-$" "")))]
      (dommy/set-html! heading
                       (goog.string/format "<a id='%s' href='#%s'>%s</a>" id id title)))))

(defn docs-subpage [doc owner]
  (reify
    om/IDidMount
    (did-mount [_] (add-link-targets (om/get-node owner)))
    om/IDidUpdate
    (did-update [_ _ _] (add-link-targets (om/get-node owner)))
    om/IRender
    (render [_]
      (html
       [:div
        (doc-utils/render-haml-template "docs_title" {:article doc})
        (if (:category doc)
          (doc-utils/render-haml-template "article_list" {:article doc :slug true})
          (doc-utils/render-haml-template (:slug doc) doc))]))))

(defrender documentation [app owner opts]
  (let [subpage (get-in app [:navigation-data :subpage])
        docs (doc-utils/find-all-docs)
        categories ((juxt :gettingstarted :languages :how-to :troubleshooting
                          :reference :parallelism :privacy-security) docs)]
    (html
     [:div.docs.page
      [:div.banner [:div.container [:h1 "Documentation"]]]
      [:div.container.content
       [:div.row
        [:aside.span3
         (doc-utils/render-haml-template "categories" {:categories categories})]
        [:div.offset1.span8
         (when subpage
           (om/build docs-search app))
         [:article
          (if-not subpage
            (om/build front-page app)
            (om/build docs-subpage (get docs subpage)))]]]]])))
