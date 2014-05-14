(ns frontend.history
  (:require [clojure.string :as string]
            [dommy.core :as dommy]
            [frontend.utils :as utils :include-macros true]
            [goog.events :as events]
            [goog.history.Html5History :as html5-history]
            [secretary.core :as sec])
  (:import [goog.history Html5History]
           [goog History]))


;; see this.transformer_ at http://goo.gl/ZHLdwa
(def ^{:doc "Custom token transformer that preserves hashes"}
  token-transformer
  (let [transformer (js/Object.)]
    (set! (.-retrieveToken transformer)
          (fn [path-prefix location]
            ;; XXX may break with advanced compilation
            (utils/inspect location)
            (str (subs (.-pathname location) (count path-prefix))
                 (when-let [hash (second (string/split (.-href location) #"#"))]
                   (str "#" hash)))))

    (set! (.-createUrl transformer)
          (fn [token path-prefix location]
            (let [[path hash] (string/split token #"#")]
              (str path-prefix
                   path
                   ;; comment out for now, need to figure out how to combine
                   ;; location search and the search already in the token
                   ;; (.-search location)
                   (when hash (str "#" hash))))))

    transformer))

(defn setup-dispatcher! [history-imp]
  (events/listen history-imp goog.history.EventType.NAVIGATE
                 #(sec/dispatch! (str "/" (aget % "token")))))

(defn setup-link-dispatcher! [history-imp top-level-node]
  (let [dom-helper (goog.dom.DomHelper.)]
    (events/listen top-level-node "click"
                   #(let [-target (.. % -target)
                          target (if (= (.-tagName -target) "A")
                                   -target
                                   (.getAncestorByTagNameAndClass dom-helper -target "A"))
                          _ (aset js/window "target" target)
                          path (when target (str (.-pathname target) (.-search target) (.-hash target)))]
                      (when (and (seq path)
                                 (not= "_blank" (.-target target))
                                 (= (.. js/window -location -hostname)
                                    (.-hostname target)))
                        (utils/mlog "navigating to" path)
                        (.setToken history-imp (subs path 1))
                        (.stopPropagation %)
                        (.preventDefault %))))))

(defn new-history-imp [top-level-node]
  ;; need a history element, or goog will overwrite the entire dom
  (dommy/append! top-level-node [:input.history.hide])
  (doto (goog.history.Html5History. js/window token-transformer)
    (.setUseFragment false)
    (.setPathPrefix "/")
    (setup-dispatcher!)
    (setup-link-dispatcher! top-level-node)
    (.setEnabled true)))
