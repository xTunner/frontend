(ns frontend.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            ;; XXX remove browser repl in prod
            [clojure.browser.repl :as repl]
            [clojure.string :as string]
            [dommy.core :as dommy]
            [goog.dom.DomHelper]
            [frontend.components.app :as app]
            [frontend.controllers.controls :as controls-con]
            [frontend.controllers.navigation :as nav-con]
            [frontend.routes :as routes]
            [frontend.controllers.api :as api-con]
            [frontend.controllers.ws :as ws-con]
            [frontend.env :as env]
            [frontend.state :as state]
            [goog.events]
            [om.core :as om :include-macros true]
            [frontend.pusher :as pusher]
            [frontend.history :as history]
            [frontend.utils :as utils :refer [mlog merror third]]
            [secretary.core :as sec])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [inspect timing swallow-errors]])
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

(defn app-state []
  (atom (assoc (state/initial-state)
          :current-user (-> js/window
                            (aget "renderContext")
                            (aget "current_user")
                            utils/js->clj-kw)
          :render-context (-> js/window
                              (aget "renderContext")
                              utils/js->clj-kw)
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

(defn log-channels?
  "Log channels in development, can be overridden by the log-channels query param"
  []
  (if (nil? (:log-channels? utils/initial-query-map))
    (env/development?)
    (:log-channels? utils/initial-query-map)))

(defn controls-handler
  [value state container]
  (when (log-channels?)
    (mlog "Controls Verbose: " value))
  (swallow-errors
   (let [previous-state @state]
     (swap! state (partial controls-con/control-event container (first value) (second value)))
     (controls-con/post-control-event! container (first value) (second value) previous-state @state))))

(defn nav-handler
  [value state history]
  (when (log-channels?)
    (mlog "Navigation Verbose: " value))
  (swallow-errors
   (let [previous-state @state]
     (swap! state (partial nav-con/navigated-to history (first value) (second value)))
     (nav-con/post-navigated-to! history (first value) (second value) previous-state @state))))

(defn api-handler
  [value state container]
  (when (log-channels?)
    (mlog "API Verbose: " (first value) (second value) (utils/third value)))
  (swallow-errors
    (let [previous-state @state]
      (swap! state (partial api-con/api-event container (first value) (second value) (utils/third value)))
      (api-con/post-api-event! container (first value) (second value) (utils/third value) previous-state @state))))

(defn ws-handler
  [value state pusher]
  (when (log-channels?)
    (mlog "websocket Verbose: " (pr-str (first value)) (second value) (utils/third value)))
  (swallow-errors
    (let [previous-state @state]
      ;; XXX: should these take the container like the rest of the controllers?
      (swap! state (partial ws-con/ws-event pusher (first value) (second value)))
      (ws-con/post-ws-event! pusher (first value) (second value) previous-state @state))))

(defn main [state top-level-node]
  (let [comms       (:comms @state)
        target-name "app"
        container   (sel1 top-level-node (str "#" target-name))
        uri-path    (.getPath utils/parsed-uri)
        history-path "/"
        history-imp (history/new-history-imp top-level-node)
        pusher-imp (pusher/new-pusher-instance)]
    (routes/define-routes! state)
    (om/root
     app/app
     state
     {:target container
      :opts {:comms comms}})
    (go (while true
          (alt!
           (:controls comms) ([v] (controls-handler v state container))
           (:nav comms) ([v] (nav-handler v state history-imp))
           (:api comms) ([v] (api-handler v state container))
           (:ws comms) ([v] (ws-handler v state pusher-imp))
           ;; Capture the current history for playback in the absence
           ;; of a server to store it
           (async/timeout 10000) (do (print "TODO: print out history: ")))))))

(defn subscribe-to-user-channel [user ws-ch]
  (put! ws-ch [:subscribe {:channel-name (pusher/user-channel user)
                           :messages [:refresh]}]))

(defn setup-browser-repl [repl-url]
  (repl/connect repl-url)
  ;; the repl tries to take over *out*, workaround for
  ;; https://github.com/cemerick/austin/issues/49
  (js/setInterval #(enable-console-print!) 1000))

(defn dispatch-to-current-location! []
  (let [uri (goog.Uri. js/document.location.href)]
    (sec/dispatch! (str (.getPath uri) (when-not (string/blank? (.getFragment uri))
                                         (str "#" (.getFragment uri)))))))


(defn handle-browser-resize
  "Handles scrolling the container on the build page to the correct position when
  the size of the browser window chagnes. Has to add an event listener at the top level."
  [app-state]
  (goog.events/listen
   js/window "resize"
   #(when (= :build (:navigation-point @app-state))
      (put! controls-ch [:container-selected (get-in @app-state state/current-container-path)]))))

(defn ^:export setup! []
  (let [state (app-state)]
    ;; globally define the state so that we can get to it for debugging
    (def debug-state state)
    (main state (sel1 :body))
    (dispatch-to-current-location!)
    (handle-browser-resize state)
    (when-let [user (:current-user @state)]
      (subscribe-to-user-channel user (get-in @state [:comms :ws])))
    (when (env/development?)
      (when-let [repl-url (get-in @state [:render-context :browser_connected_repl_url])]
        (try
          (setup-browser-repl repl-url)
          (catch js/error e
            (merror e)))))))
