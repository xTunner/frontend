(ns frontend.components.documentation
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.docs :as docs]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]))

(defrender documentation [app owner opts]
  (html [:div.docs.page
         [:div.banner
          [:div.container
           [:h1 "Documentation"]]
          [:div.container.content
           [:div.row
            [:aside.span3 "Categories placeholder"]
            [:div.span8.offset1
             "Docs search form"
             [:article
              "Docs title"
              "Search form"
              "Article list or article slug"]]]]]]))

(defrender docpage [app owner opts]
  (html
   [:div "Woop"]))

(defrender article-list-item [article]
  (html
   [:li "Hi!"]))

(defrender article-list [app owner opts]
  (let [articles []]
    (html
     [:ul.article_list
      (map #(article-list-item %) articles)])))

(defrender categories [app owner opts]
  (let [categories (document-sitemap @registered-docs)]
    (html [:ul.articles
           [:li
            [:h4
             [:a {:href (:url category)} (:title category)]
             (map #(category-child %) (:children category))]]])))
