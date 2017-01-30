(ns frontend.history
  (:require [clojure.string :as string]
            [frontend.analytics.core :as analytics]
            [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true :refer [unexterned-prop]]
            [frontend.utils.html :as html]
            [goog.events :as events]
            [goog.history.Html5History :as html5-history]
            [goog.window :as window]
            [goog.dom.classes :as classes]
            [secretary.core :as sec])
  (:import [goog.history Html5History]
           [goog.events EventType Event BrowserEvent]
           [goog History Uri]))


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

(defn set-current-token!
  "Lets us keep track of the history state, so that we don't dispatch twice on the same URL"
  [history-imp]
  (set! (.-_current_token history-imp) (.getToken history-imp)))

(defn setup-dispatcher! [history-imp]
  (events/listen history-imp goog.history.EventType.NAVIGATE
                 #(do (set-current-token! history-imp)
                      (routes/dispatch! (str "/" (.-token %))))))

(defn bootstrap-dispatcher!
  "We need lots of control over when we start listening to navigation events because
   we may want to ignore the first event if the server sends an error status code (e.g. 401)
   This function lets us ignore the first event that history-imp fires when we enable it. We'll
   manually dispatch if there is no error code from the server."
  [history-imp]
  (events/listenOnce history-imp goog.history.EventType.NAVIGATE #(setup-dispatcher! history-imp)))

(defn disable-erroneous-popstate!
  "Stops the browser's popstate from triggering NAVIGATION events unless the url has really
   changed. Fixes duplicate dispatch in Safari and the build machines."
  [history-imp]
  ;; get this history instance's version of window, might make for easier testing later
  (let [window (.-window_ history-imp)]
    (events/removeAll window goog.events.EventType.POPSTATE)
    (events/listen window goog.events.EventType.POPSTATE
                   #(if (= (.getToken history-imp)
                           (.-_current_token history-imp))
                      (utils/mlog "Ignoring duplicate dispatch event to" (.getToken history-imp))
                      (.onHistoryEvent_ history-imp %)))))

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

(defn new-window-click? [event]
  (or (.isButton event goog.events.BrowserEvent.MouseButton.MIDDLE)
      (and (.-platformModifierKey event)
           (.isButton event goog.events.BrowserEvent.MouseButton.LEFT))))

(defn closest-tag [element query-tag]
  "Handle SVG element properly."
  (let [dom-helper (goog.dom.DomHelper.)
        upper-query (string/upper-case query-tag)
        lower-query (string/lower-case query-tag)]
    (or (when (= (.-tagName element) upper-query) element)
        (.getAncestorByTagNameAndClass dom-helper element upper-query)
        (when (instance? js/SVGElement element)
          (loop [e element]
            (let [e-tag (.-tagName e)]  ; SVG tags are case sensitive
              (condp = e-tag
                lower-query e
                "svg" nil
                (recur (.-parentElement e)))))))))

(defn dispatch-link [e]
  (let [a (closest-tag (.-target e) "A")
        attributes (.-attributes a)
        href (some-> attributes .-href .-value)
        target (some-> attributes .-target .-value)
        uri (Uri/parse href)
        new-token (subs (.getPath uri) 1)]
    (when (and (not (.hasHost uri))
               (not (or (new-window-click? e)
                        (= target "_blank"))))
      (.preventDefault e)
      (if (and (route-fragment href)
               (path-matches? (.getToken history-imp) new-token))

        (do (utils/mlog "scrolling to hash for" href)
            ;; don't break the back button
            (.replaceToken history-imp new-token))

        (do (utils/mlog "navigating to" href)
            (.setToken history-imp new-token))))))

(defn setup-link-dispatcher! [history-imp top-level-node]
  (events/listen
   top-level-node "click"
   dispatch-link))

(defn new-history-imp [top-level-node]
  ;; need a history element, or goog will overwrite the entire dom
  (let [dom-helper (goog.dom.DomHelper.)
        node (.createDom dom-helper "input" #js {:class "history hide"})]
    (.append dom-helper node))
  (doto (goog.history.Html5History. js/window token-transformer)
    (.setUseFragment false)
    (.setPathPrefix "/")
    (bootstrap-dispatcher!)
    (set-current-token!) ; Stop Safari from double-dispatching
    (disable-erroneous-popstate!) ; Stop Safari from double-dispatching
    (.setEnabled true) ; This will fire a navigate event with the current token
    (setup-link-dispatcher! top-level-node)))
