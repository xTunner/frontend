(ns frontend.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [cljs-time.core :as time]
            [clojure.browser.repl :as repl]
            [clojure.string :as string]
            [dommy.core :as dommy]
            [goog.dom]
            [goog.dom.DomHelper]
            [frontend.components.app :as app]
            [frontend.controllers.controls :as controls-con]
            [frontend.controllers.navigation :as nav-con]
            [frontend.routes :as routes]
            [frontend.controllers.api :as api-con]
            [frontend.controllers.ws :as ws-con]
            [frontend.env :as env]
            [frontend.instrumentation :refer [wrap-api-instrumentation]]
            [frontend.state :as state]
            [goog.events]
            [om.core :as om :include-macros true]
            [frontend.pusher :as pusher]
            [frontend.history :as history]
            [frontend.browser-settings :as browser-settings]
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
                  :controls-mult (async/mult controls-ch)
                  :api-mult (async/mult api-ch)
                  :errors-mult (async/mult error-ch)
                  :nav-mult (async/mult navigation-ch)
                  :ws-mult (async/mult ws-ch)
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
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial controls-con/control-event container (first value) (second value)))
       (controls-con/post-control-event! container (first value) (second value) previous-state @state)))))

(defn nav-handler
  [value state history]
  (when (log-channels?)
    (mlog "Navigation Verbose: " value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial nav-con/navigated-to history (first value) (second value)))
       (nav-con/post-navigated-to! history (first value) (second value) previous-state @state)))))

(defn api-handler
  [value state container]
  (when (log-channels?)
    (mlog "API Verbose: " (first value) (second value) (utils/third value)))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state
           message (first value)
           status (second value)
           api-data (utils/third value)]
       (swap! state (wrap-api-instrumentation (partial api-con/api-event container message status api-data)
                                              api-data))
       (api-con/post-api-event! container message status api-data previous-state @state)))))

(defn ws-handler
  [value state pusher]
  (when (log-channels?)
    (mlog "websocket Verbose: " (pr-str (first value)) (second value) (utils/third value)))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       ;; XXX: should these take the container like the rest of the controllers?
       (swap! state (partial ws-con/ws-event pusher (first value) (second value)))
       (ws-con/post-ws-event! pusher (first value) (second value) previous-state @state)))))

(defn setup-timer-atom
  "Sets up an atom that will keep track of the current time.
   Used from frontend.components.common/updating-duration "
  []
  (let [mya (atom (time/now))]
    (js/setInterval #(reset! mya (time/now)) 1000)
    mya))


(defn main [state top-level-node]
  (let [comms       (:comms @state)
        target-name "om-app"
        container   (sel1 top-level-node (str "#" target-name))
        uri-path    (.getPath utils/parsed-uri)
        history-path "/"
        history-imp (history/new-history-imp top-level-node)
        pusher-imp (pusher/new-pusher-instance)
        controls-tap (chan)
        nav-tap (chan)
        api-tap (chan)
        ws-tap (chan)]
    (routes/define-routes! state)
    (om/root
     app/app
     state
     {:target container
      :shared {:comms comms
               :timer-atom (setup-timer-atom)}})

    (async/tap (:controls-mult comms) controls-tap)
    (async/tap (:nav-mult comms) nav-tap)
    (async/tap (:api-mult comms) api-tap)
    (async/tap (:ws-mult comms) ws-tap)

    (go (while true
          (alt!
           controls-tap ([v] (controls-handler v state container))
           nav-tap ([v] (nav-handler v state history-imp))
           api-tap ([v] (api-handler v state container))
           ws-tap ([v] (ws-handler v state pusher-imp))
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
    (sec/dispatch! (str (.getPath uri)
                        (when (.getQuery uri)
                          (str "?" (.getQuery uri)))
                        (when-not (string/blank? (.getFragment uri))
                          (str "#" (.getFragment uri)))))))


;; XXX this should go in IDidMount on the build container, also doesn't work
;;     if the user goes to a build page from a different page
(defn handle-browser-resize
  "Handles scrolling the container on the build page to the correct position when
  the size of the browser window chagnes. Has to add an event listener at the top level."
  [app-state]
  (goog.events/listen
   js/window "resize"
   #(when (= :build (:navigation-point @app-state))
      (put! controls-ch [:container-selected (get-in @app-state state/current-container-path)]))))

(defn apply-app-id-hack
  "Hack to make the top-level id of the app the same as the
   current knockout app. Lets us use the same stylesheet."
  []
  (goog.dom.setProperties (sel1 "#app") #js {:id "om-app"}))

(defn ^:export setup! []
  (apply-app-id-hack)
  (let [state (app-state)]
    ;; globally define the state so that we can get to it for debugging
    (def debug-state state)
    (browser-settings/setup! state)
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
