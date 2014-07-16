(ns frontend.utils.docs
  (:require [clojure.string :as string]
            [frontend.utils :as utils :include-macros true]))

(defn include-article [template-name]
  ((aget (aget js/window "HAML") template-name)))

(defn render-haml-template [template-name & [args]]
  [:div {:dangerouslySetInnerHTML
         #js {"__html"  ((aget (aget js/window "HAML") template-name)
                         (clj->js (merge {"include_article" include-article} args)))}}])

(defn new-context []
  (let [context (js/Object.)]
    (aset context "include_article" include-article)
    context))

(defn ->template-kw [template-name]
  (keyword (string/replace (name template-name) "_" "-")))

(defn article? [template-fn]
  (re-find #"this.title =" (str template-fn)))

(defn article-info [template-name template-fn]
  (let [context (new-context) ;; create an object that we can pass to the template-fn
        _ (template-fn context)       ;; writes properties into the context
        props (utils/js->clj-kw context)
        children (map ->template-kw (or (:children props) []))
        title (:title props)
        short-title (or (:short_title props) title)]
    {:template-fn template-fn
     :url (str "/docs/" (string/replace template-name "_" "-"))
     :slug template-name
     :title title
     :sort_title short-title
     :children children
     :subtitle (:subtitle props)
     :lastUpdated (:lastUpdated props)
     :category (:category props)
     :title_with_child_count (str title (when (seq children) (str "(" (count children) ")")))
     :short_title_with_child_count (str short-title (when (seq children) (str "(" (count children) ")")))}))

(defn update-children [docs]
  (reduce (fn [acc [template-name article-info]]
            (if (seq (:children article-info))
              (update-in acc [template-name :children] (fn [children]
                                                         ((apply juxt children) docs)))
              acc))
          docs docs))

(defn find-all-docs*
  "process all HAML templates, and picks the articles based on their contents
  (they write into the context, and we check for that)
   Returns an hash map of article subpages to articles."
  []
  (let [docs (reduce (fn [acc [template-name template-fn]]
                       (if (article? template-fn)
                         (let [subpage (->template-kw template-name)]
                           (assoc acc subpage (article-info template-name template-fn)))
                         acc))
                     {} (js->clj (aget js/window "HAML")))]
    (update-children docs)))

(def find-all-docs (memoize find-all-docs*))
