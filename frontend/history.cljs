(ns frontend.history
  (:require [clojure.string :as string]
            [dommy.core :as dommy]
            [frontend.utils :as utils :include-macros true]
            [goog.events :as events]
            [goog.history.Html5History :as html5-history]
            [secretary.core :as sec])
  (:require-macros [dommy.macros :refer [sel sel1]])
  (:import [goog.history Html5History]
           [goog History]))


;; see this.transformer_ at http://goo.gl/ZHLdwa
(def ^{:doc "Custom token transformer that preserves hashes"}
  token-transformer
  (let [transformer (js/Object.)]
    (set! (.-retrieveToken transformer)
          (fn [path-prefix location]
            (str (subs (.-pathname location) (count path-prefix))
                 (when-let [query (.-search location)]
                   query)
                 (when-let [hash (second (string/split (.-href location) #"#"))]
                   (str "#" hash)))))

    (set! (.-createUrl transformer)
          (fn [token path-prefix location]
            (str path-prefix token)))

    transformer))

(defn setup-dispatcher! [history-imp]
  (events/listen history-imp goog.history.EventType.NAVIGATE
                 #(sec/dispatch! (str "/" (.-token %)))))

(defn route-fragment
  "Returns the route fragment if this is a route that we've don't dispatch
  on fragments for."
  [path]
  (-> path
      sec/locate-route
      :params
      :_fragment))

(defn path-matches?
  "True if the two tokens are the same except for the fragment"
  [token-a token-b]
  (= (first (string/split token-a #"#"))
     (first (string/split token-b #"#"))))

(defn setup-link-dispatcher! [history-imp top-level-node]
  (let [dom-helper (goog.dom.DomHelper.)]
    (events/listen top-level-node "click"
                   #(let [-target (.. % -target)
                          target (if (= (.-tagName -target) "A")
                                   -target
                                   (.getAncestorByTagNameAndClass dom-helper -target "A"))
                          location (when target (str (.-pathname target) (.-search target) (.-hash target)))
                          new-token (when (seq location) (subs location 1 ))]
                      (when (and (seq location)
                                 (not= "_blank" (.-target target))
                                 (= (.. js/window -location -hostname)
                                    (.-hostname target)))
                        (.stopPropagation %)
                        (.preventDefault %)
                        (if (and (route-fragment location)
                                 (path-matches? (.getToken history-imp) new-token))
                          (do (utils/mlog "scrolling to hash for" location)
                              ;; don't break the back button
                              (.replaceToken history-imp new-token))
                          (do (utils/mlog "navigating to" location)
                              (.setToken history-imp new-token))))))))

(defn new-history-imp [top-level-node starting-location]
  ;; need a history element, or goog will overwrite the entire dom
  (let [dom-helper (goog.dom.DomHelper.)
        node (.createDom dom-helper "input" #js {:class "history hide"})]
    (.append dom-helper node))
  (doto (goog.history.Html5History. js/window token-transformer)
    (.setUseFragment false)
    (.setPathPrefix "/")
    (.setToken (subs starting-location 1))
    (setup-dispatcher!)
    (setup-link-dispatcher! top-level-node)
    (.setEnabled true)))
