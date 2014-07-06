(ns frontend.history
  (:require [clojure.string :as string]
            [dommy.core :as dommy]
            [frontend.utils :as utils :include-macros true]
            [goog.events :as events]
            [goog.history.Html5History :as html5-history]
            [secretary.core :as sec])
  (:require-macros [dommy.macros :refer [sel sel1]])
  (:import [goog.history Html5History]
           [goog.events EventType Event]
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

(defn bootstrap-dispatcher!
  "We need lots of control over when we start listening to navigation events because
   we may want to ignore the first event if the server sends an error status code (e.g. 401)
   This function lets us ignore the first event that history-imp fires when we enable it. We'll
   manually dispatch if there is no error code from the server."
  [history-imp]
  (events/listenOnce history-imp goog.history.EventType.NAVIGATE #(setup-dispatcher! history-imp)))

(defn disable-popstate!
  "Stops the browser's popstate from triggering NAVIGATION events.
   We don't ever use it and it causes double dispatching."
  [history-imp]
  ;; get this history instance's version of window, might make for easier testing later
  (let [window (.-window_ history-imp)]
    (events/removeAll window goog.events.EventType.POPSTATE)))


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
                   #(when (and (.isMouseActionButton %) ; ignore middle clicks
                               (not (.-metaKey %))) ; ignore cmd+click
                      (let [-target (.. % -target)
                            _ (set! js/window.teste %)
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
                                (.setToken history-imp new-token)))))))))

(defn new-history-imp [top-level-node]
  ;; need a history element, or goog will overwrite the entire dom
  (let [dom-helper (goog.dom.DomHelper.)
        node (.createDom dom-helper "input" #js {:class "history hide"})]
    (.append dom-helper node))
  (doto (goog.history.Html5History. js/window token-transformer)
    (.setUseFragment false)
    (.setPathPrefix "/")
    (bootstrap-dispatcher!)
    (disable-popstate!)
    ;; This will fire a navigate event with the current token
    (.setEnabled true)
    (setup-link-dispatcher! top-level-node)))
