(ns frontend.components.documentation
  (:require [clojure.string :as string]
            [dommy.core :as dommy]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.docs :as doc-utils]
            [goog.string :as gstring]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [frontend.utils :refer [defrender]]
                   [dommy.macros :refer [sel sel1]]))


(defn front-page [docs]
  [:div.row
   [:h4 "Having problems? Check these sections"]
   [:ul.articles.span4
    [:h4 "Getting started"]
    (doc-utils/render-haml-template "article_list" {:article (:gettingstarted docs) :slug true})]
   [:ul.articles.span4
    [:h4 "Troubleshooting"]
    (doc-utils/render-haml-template "article_list" {:article (:troubleshooting docs) :slug true})]])

(defn render-subpage [doc]
  [:div
   (doc-utils/render-haml-template "docs_title" {:article doc})
   (if (:category doc)
     (doc-utils/render-haml-template "article_list" {:article doc :slug true})
     (doc-utils/render-haml-template (:slug doc) doc))])

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
         (doc-utils/render-haml-template "docs_search_form")
         [:article
          (if-not subpage
            (front-page docs)
            (om/build docs-subpage (get docs subpage)))]]]]])))
