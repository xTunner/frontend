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
            [goog.string.html :as ghtml]
            goog.string.html.htmlSanitize
            [goog.string.format])
  (:require-macros [frontend.utils :refer [defrender html]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]])
  (:import [goog.events KeyCodes]))

(defn categories [docs]
  ((juxt :gettingstarted :languages :mobile :how-to :troubleshooting
         :reference :parallelism :privacy-security) docs))

(defrender markdown [markdown]
  (html
   [:div.markdown {:dangerouslySetInnerHTML
           #js {:__html  (doc-utils/render-markdown markdown)}}]))

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
         [:form.doc-search
          [:input.form-control#searchQuery
           {:type "text"
            :auto-complete "off"
            :value query
            :on-change #(utils/edit-input owner state/docs-search-path %)
            :on-key-down #(when (= (.-keyCode %) (.-ENTER KeyCodes))
                            (raise! owner [:doc-search-submitted {:query query}])
                            (.preventDefault %))
            :placeholder "What can we help you find?"
            :name "query"}]])))))

(defrender article-list [articles]
  (html
   [:ul.article-list
    (for [article articles]
      [:li {:id (str "list_entry_" (:slug article))}
       [:a {:href (:url article)} (:title_with_child_count article)]])]))

(defrender docs-categories [{:keys [categories selected]}]
  (html
   [:div
    (for [category categories]
      [:ul.nav.nav-stacked
       [:li {:id (str "category_header_" (:slug category))}
        [:a {:href (:url category)}
         [:h4 {:class (when (= (:slug category) (:slug selected)) "active")} (:title category)]]]
       (for [child (:children category)]
         [:li {:id (gstring/format "category_entry_%s_%s" (:slug category) (:slug child))}
          [:a {:href (:url child)
               :class (when (= (:slug child) (:slug selected)) "active")}
           (:short_title_with_child_count child)]])])]))

(defn search-results [query-results owner opts]
  (reify
    om/IDisplayName
    (display-name [_]
      "query-results")
    om/IRender
    (render [_]
      (let [query (:query opts)]
        (html [:div
               (cond
                (seq query-results)
                [:div
                 (when query [:p.articles-query "Articles matching \""query"\""])
                 [:ul.article-list
                  (for [result query-results]
                    [:li
                     [:a {:href (:url result)} (:title result)]
                     [:p {:dangerouslySetInnerHTML {:__html  (ghtml/htmlSanitize (:snippet_text result))}}]
                     ])]]
                query
                [:p "No articles found matching \"" [:strong query] "\""]
                :else
                [:p "Type your query and press enter to search."])])))))

(defrender front-page [app owner]
  (let [docs (get-in app state/docs-data-path)]
    (html
     [:div.front-page-categories
      (for [category (categories docs)]
        (when-let [slug (:slug category)]
          (let [children (:children category)]
            [:div.front-page-category
             [:img.logo {:id (gstring/format "doc-image-%s" slug)
                    :src (-> "/img/outer/docs/%s.svg" (gstring/format slug) utils/cdn-path)}]
             [:h3 (:title category)]
             [:ul.list-unstyled
              [:li [:a {:href (-> children first :url)}
                    (-> children first :title)]]
              [:li [:a {:href (:url category)} [:em (gstring/format "%d more" (-> children rest count))]]]]])))])))

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
    [:h1 (:title doc)]
    (if-not (empty? (:children doc))
      (om/build article-list (:children doc))
      (if (:markdown doc)
        (om/build subpage-content doc {:opts opts})
        [:div.loading-spinner common/spinner]))]))

(defrender documentation [app owner opts]
  (let [subpage (get-in app [:navigation-data :subpage])
        fragment (get-in app [:navigation-data :_fragment])
        docs (-> app (get-in state/docs-data-path) (assoc-in [subpage :active] true))
        doc (get docs subpage)
        query (get-in app state/docs-articles-results-query-path)
        query-results (get-in app state/docs-articles-results-path)]
    (html
     [:div.docs.page
      [:div.content
       [:aside
        (om/build docs-categories {:categories (categories docs) :selected doc})]
       [:div.container-fluid.article-container
        [:div.row
         [:div.col-sm-12
          [:article
           (om/build docs-search app)
           (if (= subpage :search)
             (om/build search-results query-results {:opts {:query query}})
             [:div
              (when query
                [:p.back-to-search [:a {:href "/docs/search"} (gstring/format "Back to search results for \"%s\"" query)]])
              (if subpage
                (om/build docs-subpage doc {:opts {:_fragment fragment}})
                (om/build front-page app))])]]]]]])))
