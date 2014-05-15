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
            [frontend.history :as history]
            [frontend.utils :as utils :refer [mlog merror third]]
            [secretary.core :as sec])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [inspect timing]])
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
                               (swap! state (partial nav-con/navigated-to history-imp (first v) (second v)))
                               (nav-pcon/post-navigated-to! history-imp (first v) (second v) previous-state @state))
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

(defn setup-browser-repl []
  (when-let [repl-url (aget js/window "browser_connected_repl_url")]
    (try
      (repl/connect repl-url)
      ;; the repl tries to take over *out*, workaround for
      ;; https://github.com/cemerick/austin/issues/49
      (js/setInterval #(enable-console-print!) 1000)
      (catch js/Error e
        (merror e)))))

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
      (put! controls-ch [:container-selected (get-in @app-state [:current-build :current-container-id] 0)]))))

(defn ^:export setup! []
  (let [state (app-state)]
    (main state (sel1 :body))
    (dispatch-to-current-location!)
    (handle-browser-resize state)
    (when-let [user (:current-user @state)]
      (subscribe-to-user-channel user (get-in @state [:comms :ws])))
    (when (env/development?)
      (setup-browser-repl))))
