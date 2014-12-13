(ns frontend.components.documentation
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [clojure.string :as string]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.components.common :as common]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.docs :as doc-utils]
            [goog.dom]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn docs-search [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
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
                   (raise! owner [:edited-input {:path state/docs-search-path
                                                 :value query}])
                   (raise! owner [:doc-search-submitted {:query query}])
                   query)}))
    om/IRender
    (render [_]
      (let [query (get-in app state/docs-search-path)]
        (html
         [:form#searchDocs.clearfix.form-search
          [:input#searchQuery
           {:type "text",
            :value query
            :on-change #(utils/edit-input owner state/docs-search-path %)
            :placeholder "What can we help you find?",
            :name "query"}]
          [:button {:on-click #(do (raise! owner [:doc-search-submitted {:query query}])
                                   false)
                    :type "submit"}
           [:i.fa.fa-search]]])))))

(defrender article-list [articles]
  (html
   [:ul.article_list
    (for [article articles]
      [:li {:id (str "list_entry_" (:slug article))}
       [:a {:href (:url article)} (:title_with_child_count article)]])]))

(defrender docs-category [category]
  (html
   [:ul.articles
    [:li {:id (str "category_header_" (:slug category))}
     [:h4
      [:a {:href (:url category)} (:title category)]]]
   (for [child (:children category)]
      [:li {:id (gstring/format "category_entry_%_%" (:slug category) (:slug child))}
       [:a {:href (:url child)} (:short_title_with_child_count child)]])]))

(defrender docs-categories [categories]
  (html
   [:div
    (om/build-all docs-category categories)]))

(defrender docs-title [doc]
  (html
   [:div
    [:h1 (:title doc)]
    (when-let [last-updated (:lastUpdated doc)]
      [:p.meta [:strong "Last Updated "] last-updated])]))

(defrender front-page [app owner]
  (let [query-results (get-in app state/docs-articles-results-path)
        query (get-in app state/docs-articles-results-query-path)
        docs (get-in app state/docs-data-path)]
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
        (om/build article-list (get-in docs [:gettingstarted :children]))]
       [:ul.articles.span4
        [:h4 "Troubleshooting"]
        (om/build article-list (get-in docs [:troubleshooting :children]))]]])))

(defn add-link-targets [node]
  (doseq [tag ["h2" "h3" "h3" "h4" "h5" "h6"]
          heading (utils/node-list->seqable (goog.dom/getElementsByTagNameAndClass tag nil node))]
    (let [title (utils/text heading)
          id (if-not (string/blank? (.-id heading))
               (.-id heading)
               (-> title
                   string/lower-case
                   string/trim
                   (string/replace \' "")     ; heroku's -> herokus
                   (string/replace #"[^a-z0-9]+" "-") ; dashes
                   (string/replace #"^-" "") ; don't let first or last be dashes
                   (string/replace #"-$" "")))]
      (utils/set-html! heading
                       (gstring/format "<a id='%s' href='#%s'>%s</a>" id id title)))))

(defrender markdown [markdown]
  (html
   [:span {:dangerouslySetInnerHTML
           #js {:__html  (doc-utils/render-markdown markdown)}}]))

(defn subpage-content [doc owner opts]
  (reify
    om/IDidMount
    (did-mount [_]
      (add-link-targets (om/get-node owner))
      (when-let [fragment (:_fragment opts)]
        (utils/scroll-to-id! fragment)))
    om/IDidUpdate
    (did-update [_ _ _]
      ;; TODO: Move this to the markdown rendering process
      (add-link-targets (om/get-node owner)))
    om/IRender
    (render [_]
      (html
       (om/build markdown (:markdown doc))))))

(defrender docs-subpage [doc owner opts]
  (html
   [:div
    (om/build docs-title doc)
    (if-not (empty? (:children doc))
      (om/build article-list (:children doc))
      (if (:markdown doc)
        (om/build subpage-content doc {:opts opts})
        [:div.loading-spinner common/spinner]))]))

(defrender documentation [app owner opts]
  (let [subpage (get-in app [:navigation-data :subpage])
        fragment (get-in app [:navigation-data :_fragment])
        docs (get-in app state/docs-data-path)
        categories ((juxt :gettingstarted :languages :how-to :troubleshooting
                          :reference :parallelism :privacy-security) docs)]
    (html
     [:div.docs.page
      [:div.banner [:div.container [:h1 "Documentation"]]]
      [:div.container.content
       [:div.row
        [:aside.span3
        (om/build docs-categories categories)]
        [:div.offset1.span8
         (when subpage
           (om/build docs-search app))
         [:article
          (if-not subpage
            (om/build front-page app)
            (om/build docs-subpage (get docs subpage) {:opts {:_fragment fragment}}))]]]]])))
