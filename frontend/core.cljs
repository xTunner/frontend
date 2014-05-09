(ns frontend.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            ;; XXX remove browser repl in prod
            [clojure.browser.repl :as repl]
            [dommy.core :as dommy]
            [goog.dom.DomHelper]
            [frontend.components.app :as app]
            [frontend.controllers.controls :as controls-con]
            [frontend.controllers.navigation :as nav-con]
            [frontend.controllers.post-controls :as controls-pcon]
            [frontend.controllers.post-navigation :as nav-pcon]
            [frontend.routes :as routes]
            [frontend.controllers.api :as api-con]
            [frontend.controllers.post-api :as api-pcon]
            [frontend.controllers.ws :as ws-con]
            [frontend.controllers.post-ws :as ws-pcon]
            [frontend.env :as env]
            [frontend.state :as state]
            [goog.events]
            [om.core :as om :include-macros true]
            [frontend.pusher :as pusher]
            [frontend.utils :as utils :refer [mlog merror third]]
            [secretary.core :as sec])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [inspect]])
  (:use-macros [dommy.macros :only [node sel sel1]]))

(enable-console-print!)

;; Overcome some of the browser limitations around DnD
(def mouse-move-ch
  (chan (sliding-buffer 1)))

(def mouse-down-ch
  (chan (sliding-buffer 1)))

(def mouse-up-ch
  (chan (sliding-buffer 1)))

(js/window.addEventListener "mousedown" #(put! mouse-down-ch %))
(js/window.addEventListener "mouseup"   #(put! mouse-up-ch   %))
(js/window.addEventListener "mousemove" #(put! mouse-move-ch %))

(def controls-ch
  (chan))

(def api-ch
  (chan))

(def error-ch
  (chan))

(def navigation-ch
  (chan))

(def ^{:doc "websocket channel"}
  ws-ch
  (chan))

(def app-state
  (atom (assoc (state/initial-state)
          :current-user (-> js/window
                            (aget "renderContext")
                            (aget "current_user")
                            (js->clj :keywordize-keys true))
          :render-context (-> js/window
                              (aget "renderContext")
                              (js->clj :keywordize-keys true))
          :comms {:controls  controls-ch
                  :api       api-ch
                  :errors    error-ch
                  :nav       navigation-ch
                  :ws        ws-ch
                  :mouse-move {:ch mouse-move-ch
                               :mult (async/mult mouse-move-ch)}
                  :mouse-down {:ch mouse-down-ch
                               :mult (async/mult mouse-down-ch)}
                  :mouse-up {:ch mouse-up-ch
                             :mult (async/mult mouse-up-ch)}})))

(defn main [state top-level-node]
  (let [comms      (:comms @state)
        target-name                           "app"
        container                             (sel1 top-level-node (str "#" target-name))
        uri-path                              (.getPath utils/parsed-uri)
        [_ maybe-deep-link]                   (re-matches #"^/app/(.*)" uri-path)
        history-path "/";;(str (.getPath (goog.Uri. js/document.location.href)) "/")
        history-el (dommy/append! top-level-node [:input.history {:style "display:none"}])
        _ (print "history-path: " history-path)
        history-imp (doto (goog.history.Html5History.)
                      (.setUseFragment false)
                      (.setPathPrefix history-path))
        dom-helper (goog.dom.DomHelper.)
        pusher-imp (pusher/new-pusher-instance)]
    ;; XXX: Don't store this in state, it's not serializeable
    (swap! state assoc :history-imp history-imp)
    (js/console.log "history-imp " history-imp)
    (print "Target-name: " target-name)
    (print "Container: " container)
    (print "tln: " top-level-node)
    (print "history-el" history-el)
    (when history-el
      (goog.events/listen top-level-node "click"
                          #(let [-target (.. % -target)
                                 target (if (= (.-tagName -target) "A")
                                          -target
                                          (.getAncestorByTagNameAndClass dom-helper -target "A"))
                                 path (when target (.-pathname target))]
                             (when (seq path)
                               ;; XXX this doesn't work with hashes
                               (.setToken history-imp (subs path 1 (count path)))
                               (.stopPropagation %)
                               (.preventDefault %))))
      ;; If we don't have a history-el, gclosure will literally
      ;; destroy the entire document to insert its hidden history
      ;; element via document.write()
      (print "defining routes")
      (routes/define-routes! state history-imp))
    (om/root
     app/app
     state
     {:target container
      :opts {:comms comms}})
    (go (while true
          (alt!
           (:controls comms) ([v]
                                (when true (:log-channels? utils/initial-query-map)
                                      (mlog "Controls Verbose: " v))
                                (try
                                  (let [previous-state @state]
                                    (swap! state (partial controls-con/control-event container (first v) (second v)))
                                    (controls-pcon/post-control-event! container (first v) (second v) previous-state @state))
                                  (catch js/Error e
                                    (merror e)
                                    (when (:rethrow-errors? utils/initial-query-map)
                                      (throw e)))))
           (:nav comms) ([v]
                           (when true (:log-channels? utils/initial-query-map)
                                 (mlog "Navigation Verbose: " v))
                           (try
                             (let [previous-state @state]
                               (swap! state (partial nav-con/navigated-to container (first v) (second v)))
                               (nav-pcon/post-navigated-to! container (first v) (second v) previous-state @state))
                             (catch js/Error e
                               (merror e)
                               (when (:rethrow-errors? utils/initial-query-map)
                                 (throw e)))))
           (:api comms) ([v]
                           (when true (:log-channels? utils/initial-query-map)
                                 (mlog "API Verbose: " (first v) (second v) (drop 2 v)))
                           (try
                             (let [previous-state @state]
                               (swap! state (partial api-con/api-event container (first v) (second v) (utils/third v)))
                               (api-pcon/post-api-event! container (first v) (second v) (utils/third v) previous-state @state))
                             (catch js/Error e
                               (merror e)
                               (when (:rethrow-errors? utils/initial-query-map)
                                 (throw e)))))
           (:ws comms) ([v]
                           (when true (:log-channels? utils/initial-query-map)
                                 (mlog "websocket Verbose: " (pr-str (first v)) (second v) (drop 2 v)))
                           (try
                             (let [previous-state @state]
                               ;; XXX: should these take the container like the rest of the controllers?
                               (swap! state (partial ws-con/ws-event pusher-imp (first v) (second v)))
                               (ws-pcon/post-ws-event! pusher-imp (first v) (second v) previous-state @state))
                             (catch js/Error e
                               (merror e)
                               (when (:rethrow-errors? utils/initial-query-map)
                                 (throw e)))))
           ;; Capture the current history for playback in the absence
           ;; of a server to store it
           (async/timeout 10000) (do (print "TODO: print out history: ")))))))

(defn subscribe-to-user-channel [user ws-ch]
  (put! ws-ch [:subscribe {:channel-name (pusher/user-channel user)
                           :messages [:refresh]}]))

(defn setup! []
  (main app-state (sel1 :body))
  ;; XXX direct dispatching is probably the wrong approach
  (when-let [user (inspect (:current-user @app-state))]
    (subscribe-to-user-channel user (inspect (get-in @app-state [:comms :ws]))))
  (when (env/development?)
    (when-let [repl-url (aget js/window "browser_connected_repl_url")]
      (try
        (repl/connect repl-url)
        (catch js/Error e
          (merror e)))))
  (sec/dispatch! (.getPath (goog.Uri. js/document.location.href))))

;; Wait for the page to finish loading before we kick off the setup
;; process
(set! (.-onload js/window) setup!)
