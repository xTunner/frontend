(ns frontend.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [clojure.string :as string]
            [goog.dom]
            [goog.dom.DomHelper]
            [goog.dom.classlist]
            [frontend.ab :as ab]
            [frontend.analytics :as analytics]
            [frontend.components.app :as app]
            [frontend.config :as config]
            [frontend.controllers.controls :as controls-con]
            [frontend.controllers.navigation :as nav-con]
            [frontend.routes :as routes]
            [frontend.controllers.api :as api-con]
            [frontend.controllers.ws :as ws-con]
            [frontend.controllers.errors :as errors-con]
            [frontend.extensions]
            [frontend.instrumentation :as instrumentation :refer [wrap-api-instrumentation]]
            [frontend.state :as state]
            [goog.events]
            [om.core :as om :include-macros true]
            [frontend.pusher :as pusher]
            [frontend.history :as history]
            [frontend.browser-settings :as browser-settings]
            [frontend.utils :as utils :refer [mlog merror third set-canonical!]]
            [frontend.datetime :as datetime]
            [frontend.timer :as timer]
            [frontend.support :as support]
            [secretary.core :as sec]
            ;; Extends goog.date.* datatypes to IEquiv and IComparable.
            [cljs-time.extend])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [inspect timing swallow-errors]]
                   [frontend.devtools :refer [require-devtools!]]))

(require-devtools!)

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

(def errors-ch
  (chan))

(def navigation-ch
  (chan))

(def ^{:doc "websocket channel"}
  ws-ch
  (chan))

(defn get-ab-overrides []
  (merge (some-> js/window
                 (aget "renderContext")
                 (aget "abOverrides")
                 (utils/js->clj-kw))))

(defn set-ab-override [test-name value]
  (when (nil? (aget js/window "renderContext" "abOverrides"))
    (aset js/window "renderContext" "abOverrides" #js {}))
  (aset js/window "renderContext" "abOverrides" (name test-name) value))

(defn get-ab-tests [ab-test-definitions]
  (let [overrides (get-ab-overrides)]
    (ab/setup! ab-test-definitions :overrides overrides)))

(defn app-state []
  (let [initial-state (state/initial-state)]
    (atom (assoc initial-state
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
                      :ws        ws-ch
                      :controls-mult (async/mult controls-ch)
                      :api-mult (async/mult api-ch)
                      :errors-mult (async/mult errors-ch)
                      :nav-mult (async/mult navigation-ch)
                      :ws-mult (async/mult ws-ch)
                      :mouse-move {:ch mouse-move-ch
                                   :mult (async/mult mouse-move-ch)}
                      :mouse-down {:ch mouse-down-ch
                                   :mult (async/mult mouse-down-ch)}
                      :mouse-up {:ch mouse-up-ch
                                 :mult (async/mult mouse-up-ch)}}))))

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
       (controls-con/post-control-event! container (first value) (second value) previous-state @state)))
   (analytics/track-message (first value) (second value) state)))

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
       (analytics/register-last-touch-utm query-params)
       (when-let [join (:join query-params)] (analytics/track-join-code join))
       (analytics/track-view-page (if inner? :inner :outer))
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

(declare reinstall-om!)

(defn install-om [state ab-tests container comms instrument?]
  (om/root
   app/app
   state
   {:target container
    :shared {:comms comms
             ;; note that changing ab-tests dynamically requires reinstalling om
             :ab-tests ab-tests
             :timer-atom (timer/initialize)
             :_app-state-do-not-use state}
    ;; :instrument (let [methods (cond-> om/pure-methods
    ;;                             instrument? instrumentation/instrument-methods)
    ;;                   descriptor (om/specify-state-methods! (clj->js methods))]
    ;;               (fn [f cursor m]
    ;;                 (om/build* f cursor (assoc m :descriptor descriptor))))
    :opts {:reinstall-om! reinstall-om!}}))

(defn find-top-level-node []
  (.-body js/document))

(defn find-app-container []
  (goog.dom/getElement "om-app"))

(defn main [state ab-tests top-level-node history-imp instrument?]
  (let [comms       (:comms @state)
        container   (find-app-container)
        uri-path    (.getPath utils/parsed-uri)
        pusher-imp (pusher/new-pusher-instance (config/pusher))
        controls-tap (chan)
        nav-tap (chan)
        api-tap (chan)
        ws-tap (chan)
        errors-tap (chan)]
    (routes/define-routes! state)
    (install-om state ab-tests container comms instrument?)

    (async/tap (:controls-mult comms) controls-tap)
    (async/tap (:nav-mult comms) nav-tap)
    (async/tap (:api-mult comms) api-tap)
    (async/tap (:ws-mult comms) ws-tap)
    (async/tap (:errors-mult comms) errors-tap)

    (go (while true
          (alt!
           controls-tap ([v] (controls-handler v state container))
           nav-tap ([v] (nav-handler v state history-imp))
           api-tap ([v] (api-handler v state container))
           ws-tap ([v] (ws-handler v state pusher-imp))
           errors-tap ([v] (errors-handler v state container))
           ;; Capture the current history for playback in the absence
           ;; of a server to store it
           (async/timeout 10000) (do #_(print "TODO: print out history: ")))))))

(defn subscribe-to-user-channel [user ws-ch]
  (put! ws-ch [:subscribe {:channel-name (pusher/user-channel user)
                           :messages [:refresh]}]))

(defn apply-app-id-hack
  "Hack to make the top-level id of the app the same as the
   current knockout app. Lets us use the same stylesheet."
  []
  (goog.dom.setProperties (goog.dom/getElement "app") #js {:id "om-app"}))

(defn ^:export toggle-admin []
  (swap! state/debug-state update-in [:current-user :admin] not))

(defn ^:export toggle-dev-admin []
  (swap! state/debug-state update-in [:current-user :dev-admin] not))

(defn ^:export explode []
  (swallow-errors
    (assoc [] :deliberate :exception)))

(defn ^:export set-ab-test
  "Debug function for setting ab-tests, call from the js console as frontend.core.set_ab_test('new_test', false)"
  [test-name value]
  (let [test-key (keyword (name test-name))]
    (println "starting value for" test-name "was" (-> @state/debug-state
                                                      :ab-test-definitions
                                                      get-ab-tests
                                                      test-key))
    (set-ab-override (name test-name) value)
    (reinstall-om!)
    (println "value for" test-name "is now" (-> @state/debug-state
                                                :ab-test-definitions
                                                get-ab-tests
                                                test-key))))

(aset js/window "set_ab_test" set-ab-test)

(defn ^:export app-state-to-js
  "Used for inspecting app state in the console."
  []
  (clj->js @state/debug-state))

(aset js/window "app_state_to_js" set-ab-test)


(defn ^:export reinstall-om! []
  (install-om state/debug-state (get-ab-tests (:ab-test-definitions @state/debug-state)) (find-app-container) (:comms @state/debug-state) true))

(defn add-css-link [path]
  (let [link (goog.dom/createDom "link"
               #js {:rel "stylesheet"
                    :href (str path "?t=" (.getTime (js/Date.)))})]
    (.appendChild (.-head js/document) link)))

(defn track-intercom-widget! [state]
  (when (config/intercom-enabled?)
    ;; wait a little bit for the intercom widget to load and install itself
    (js/setTimeout
     (fn []
       (when-let [launcher (goog.dom/getElement "intercom-launcher")]
         (goog.events/listen
          launcher
          goog.events.EventType.CLICK
          (fn []
            ;; this matches the :support-dialog-raised control event
            ;; raised by the Support aside
            (analytics/track-message "support-dialog-raised" {} state)))))
     5000)))

(defn track-elevio-widget! [state]
  (when (config/elevio-enabled?)
    (js/setTimeout
     (fn []
       (when-let [launcher (goog.dom/getElement "elevio-base-menu")]
         (goog.events/listen
          launcher
          goog.events.EventType.CLICK
          (fn [e]
            (let [target (.-target e)
                  module-name (-> (or (-> target
                                          (goog.dom/getAncestorByTagNameAndClass "div")
                                          (.getAttribute "data-elevio-tooltip"))
                                      "widget")
                                  (string/lower-case)
                                  (string/replace #"\s+" "-"))
                  message (str "elevio-" module-name "-clicked")]
             ;; This triggers when the elevio widget is clicked
             (analytics/track message))))))
     5000)))

(defn ^:export setup! []
  (apply-app-id-hack)
  (analytics/set-existing-user)
  (support/enable-one!)
  (let [state (app-state)
        top-level-node (find-top-level-node)
        history-imp (history/new-history-imp top-level-node)
        instrument? (get-in @state [:render-context :instrument])
        ab-tests (get-ab-tests (:ab-test-definitions @state))]
    ;; globally define the state so that we can get to it for debugging
    (set! state/debug-state state)
    (when instrument?
      (instrumentation/setup-component-stats!))
    (browser-settings/setup! state)
    (if (config/elevio-enabled?)
      (track-elevio-widget! state)
      (track-intercom-widget! state))
    (main state ab-tests top-level-node history-imp instrument?)
    (if-let [error-status (get-in @state [:render-context :status])]
      ;; error codes from the server get passed as :status in the render-context
      (put! (get-in @state [:comms :nav]) [:error {:status error-status}])
      (do (analytics/track-path (str "/" (.getToken history-imp)))
          (routes/dispatch! (str "/" (.getToken history-imp)))))
    (when-let [user (:current-user @state)]
      (subscribe-to-user-channel user (get-in @state [:comms :ws]))
      (analytics/init-user (:login user)))
    (analytics/track-invited-by (:invited-by utils/initial-query-map))))
