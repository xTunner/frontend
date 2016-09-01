(ns frontend.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [clojure.string :as string]
            [goog.dom]
            [goog.dom.DomHelper]
            [goog.dom.classlist]
            [frontend.ab :as ab]
            [frontend.analytics.core :as analytics]
            [frontend.api :as api]
            [frontend.components.app :as app]
            [frontend.config :as config]
            [frontend.controllers.controls :as controls-con]
            [frontend.controllers.navigation :as nav-con]
            [frontend.routes :as routes]
            [frontend.controllers.api :as api-con]
            [frontend.controllers.ws :as ws-con]
            [frontend.controllers.errors :as errors-con]
            [frontend.extensions]
            [frontend.instrumentation :refer [wrap-api-instrumentation]]
            [frontend.state :as state]
            [goog.events :as gevents]
            [om.core :as om :include-macros true]
            [frontend.pusher :as pusher]
            [frontend.history :as history]
            [frontend.browser-settings :as browser-settings]
            [frontend.utils :as utils :refer [mlog merror third set-canonical!]]
            [frontend.datetime :as datetime]
            [frontend.timer :as timer]
            [frontend.support :as support]
            [schema.core :as s :include-macros true]
            [secretary.core :as sec]
            ;; Extends goog.date.* datatypes to IEquiv and IComparable.
            [cljs-time.extend]
            [cljsjs.react]
            [figwheel.client.utils])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [inspect timing swallow-errors]]
                   [frontend.devtools :refer [require-devtools!]]))

(when config/client-dev?
  (enable-console-print!)
  (require-devtools!)
  (s/set-fn-validation! true))


(def controls-ch
  (chan))

(def api-ch
  (chan))

(def errors-ch
  (chan))

(def navigation-ch
  (chan))

(def ^{:doc "websocket channel"}
  ws-ch
  (chan))

(defn initial-state
  "Builds the initial app state, including :comms and data that comes from the
  renderContext."
  []
  (assoc state/initial-state
         :current-user (-> js/window
                           (aget "renderContext")
                           (aget "current_user")
                           utils/js->clj-kw)
         :render-context (-> js/window
                             (aget "renderContext")
                             utils/js->clj-kw)
         :comms {:controls  controls-ch
                 :api       api-ch
                 :errors    errors-ch
                 :nav       navigation-ch
                 :ws        ws-ch}))

(defn log-channels?
  "Log channels in development, can be overridden by the log-channels query param"
  []
  (:log-channels? utils/initial-query-map (config/log-channels?)))

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
  [[navigation-point {:keys [inner? query-params] :as args} :as value] state history]
  (when (log-channels?)
    (mlog "Navigation Verbose: " value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial nav-con/navigated-to history navigation-point args))
       (nav-con/post-navigated-to! history navigation-point args previous-state @state)
       (set-canonical! (:_canonical args))
       (when-not (= navigation-point :navigate!)
         (analytics/track {:event-type :pageview
                           :navigation-point navigation-point
                           :current-state @state}))
       (when-let [app-dominant (goog.dom.getElementByClass "app-dominant")]
         (set! (.-scrollTop app-dominant) 0))
       (when-let [main-body (goog.dom.getElementByClass "main-body")]
         (set! (.-scrollTop main-body) 0))))))

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
       (when-let [date-header (get-in api-data [:response-headers "Date"])]
         (datetime/update-server-offset date-header))
       (api-con/post-api-event! container message status api-data previous-state @state)))))

(defn ws-handler
  [value state pusher]
  (when (log-channels?)
    (mlog "websocket Verbose: " (pr-str (first value)) (second value) (utils/third value)))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial ws-con/ws-event pusher (first value) (second value)))
       (ws-con/post-ws-event! pusher (first value) (second value) previous-state @state)))))

(defn errors-handler
  [value state container]
  (when (log-channels?)
    (mlog "Errors Verbose: " value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial errors-con/error container (first value) (second value)))
       (errors-con/post-error! container (first value) (second value) previous-state @state)))))

(defn mount-om [state-atom container comms]
  (om/root
   app/app
   state-atom
   {:target container
    :shared {:comms comms
             :timer-atom (timer/initialize)
             :_app-state-do-not-use state-atom
             :track-event #(analytics/track (assoc % :current-state @state-atom))}}))

(defn find-top-level-node []
  (.-body js/document))

(defn find-app-container []
  (goog.dom/getElement "app"))

(defn subscribe-to-user-channel [user ws-ch]
  (put! ws-ch [:subscribe {:channel-name (pusher/user-channel user)
                           :messages [:refresh]}]))

(defn ^:export setup! []
  (support/enable-one!)
  (let [state (initial-state)
        comms (:comms state)
        state-atom (atom state)
        top-level-node (find-top-level-node)
        container (find-app-container)
        history-imp (history/new-history-imp top-level-node)
        pusher-imp (pusher/new-pusher-instance (config/pusher))]

    ;; globally define the state so that we can get to it for debugging
    (set! state/debug-state state-atom)

    (browser-settings/setup! state-atom)

    (routes/define-routes! state)

    (mount-om state-atom container comms)

    (when config/client-dev?
      ;; Re-mount Om app when Figwheel reloads.
      (gevents/listen js/document.body
                      "figwheel.js-reload"
                      #(mount-om state-atom container comms)))

    (go
      (while true
        (alt!
          (:controls comms) ([v] (controls-handler v state-atom container))
          (:nav comms) ([v] (nav-handler v state-atom history-imp))
          (:api comms) ([v] (api-handler v state-atom container))
          (:ws comms) ([v] (ws-handler v state-atom pusher-imp))
          (:errors comms) ([v] (errors-handler v state-atom container)))))

    (when (config/enterprise?)
      (api/get-enterprise-site-status (:api comms)))

    (if-let [error-status (get-in state [:render-context :status])]
      ;; error codes from the server get passed as :status in the render-context
      (put! (get-in state [:comms :nav]) [:error {:status error-status}])
      (routes/dispatch! (str "/" (.getToken history-imp))))
    (when-let [user (:current-user state)]
      (analytics/track {:event-type :init-user
                        :current-state state})
      (subscribe-to-user-channel user (get-in state [:comms :ws])))))



(defn ^:export toggle-admin []
  (swap! state/debug-state update-in [:current-user :admin] not))

(defn ^:export toggle-dev-admin []
  (swap! state/debug-state update-in [:current-user :dev-admin] not))

(defn ^:export explode []
  (swallow-errors
    (assoc [] :deliberate :exception)))

(defn ^:export app-state-to-js
  "Used for inspecting app state in the console."
  []
  (clj->js @state/debug-state))

(aset js/window "app_state_to_js" app-state-to-js)


;; Figwheel offers an event when JS is reloaded, but not when CSS is reloaded. A
;; PR is waiting to add this; until then, fire that event from here.
;; See: https://github.com/bhauman/lein-figwheel/pull/463
(defn handle-css-reload [files]
  (figwheel.client.utils/dispatch-custom-event "figwheel.css-reload" files))
