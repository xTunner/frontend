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
            [om.next :as om-next :refer-macros [defui]]
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


(defn initial-state
  "Builds the initial app state, including data that comes from the
  renderContext."
  []
  (assoc state/initial-state
         :current-user (-> js/window
                           (aget "renderContext")
                           (aget "current_user")
                           utils/js->clj-kw)
         :render-context (-> js/window
                             (aget "renderContext")
                             utils/js->clj-kw)))

(defn log-channels?
  "Log channels in development, can be overridden by the log-channels query param"
  []
  (:log-channels? utils/initial-query-map (config/log-channels?)))

(defn controls-handler
  [value state container comms]
  (when (log-channels?)
    (mlog "Controls Verbose: " value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial controls-con/control-event container (first value) (second value)))
       (controls-con/post-control-event! container (first value) (second value) previous-state @state comms)))))

;; NOMERGE
;; For the purposes of the spike:
;; - Messages on the nav channel cause a mutation that causes the route to change.
;; - The mutation calls the nav-handler.
;; - We throw away the :navigation-point set by the nav-handler.
;; - We assoc the current route into the legacy state as :navigation-point.
(defn nav-handler
  [[navigation-point {:keys [inner? query-params] :as args} :as value] state history comms]
  (when (log-channels?)
    (mlog "Navigation Verbose: " value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (comp #(dissoc % :navigation-point)
                          (partial nav-con/navigated-to history navigation-point args)))
       (nav-con/post-navigated-to! history navigation-point args previous-state @state comms)
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
  [value state container comms]
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
       (api-con/post-api-event! container message status api-data previous-state @state comms)))))

(defn ws-handler
  [value state pusher comms]
  (when (log-channels?)
    (mlog "websocket Verbose: " (pr-str (first value)) (second value) (utils/third value)))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial ws-con/ws-event pusher (first value) (second value)))
       (ws-con/post-ws-event! pusher (first value) (second value) previous-state @state comms)))))

(defn errors-handler
  [value state container comms]
  (when (log-channels?)
    (mlog "Errors Verbose: " value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial errors-con/error container (first value) (second value)))
       (errors-con/post-error! container (first value) (second value) previous-state @state comms)))))

(defn find-top-level-node []
  (.-body js/document))

(defn find-app-container []
  (goog.dom/getElement "app"))

(defn subscribe-to-user-channel [user ws-ch]
  (put! ws-ch [:subscribe {:channel-name (pusher/user-channel user)
                           :messages [:refresh]}]))

(defmulti read om-next/dispatch)

(defmethod read :legacy/state
  [{:keys [state] :as env} key params]
  {:value (get @state key)})

(defmethod read ::route
  [{:keys [state]} key _]
  {:value (get @state key)})


(defmulti mutate om-next/dispatch)

;; Based on Compassus's parser.
(defmethod mutate `set-route!
  [{:keys [state] :as env} key params]
  (let [{:keys [route]} params]
    {:value {:keys [::route ::route-data]}
     :action #(swap! state assoc ::route route)}))

(def parser (om-next/parser {:read read :mutate mutate}))

;; NOMERGE This is only a var so that toggle-admin and toggle-dev-admin can work.
(defonce reconciler nil)

;; Wraps an atom, but only exposes the portion of its value at path.
(deftype LensedAtom [atom path]
  IDeref
  (-deref [_] (get-in (deref atom) path))

  ISwap
  (-swap! [_ f] (swap! atom update-in path f))
  (-swap! [_ f a] (swap! atom update-in path f a))
  (-swap! [_ f a b] (swap! atom update-in path f a b))
  (-swap! [_ f a b xs] (apply swap! atom update-in path f a b xs))

  IWatchable
  ;; We don't need to notify watches, because our parent atom does that.
  (-notify-watches [_ _ _] nil)
  (-add-watch [this key f]
    ;; "Namespace" the key in the parent's watches with this object.
    (add-watch atom [this key]
               (fn [[_ key] _ old-state new-state]
                 (f key this
                    (get-in old-state path)
                    (get-in new-state path))))
    this)
  (-remove-watch [this key]
    (remove-watch atom [this key])))

(defui ^:once Root
  static om-next/IQuery
  (query [this]
    [::route :legacy/state])
  Object
  (render [this]
    ;; The legacy-state-atom is a LensedAtom which we can treat like a
    ;; normal atom, but which presents only the legacy state.
    (let [legacy-state-atom (LensedAtom. (om-next/app-state (om-next/get-reconciler this)) [:legacy/state])]

      ;; Om Prev, like Om Next, has a mechanism to queue component state changes.
      ;; Unfortunately there's no great way to connect the two, so we abandon Om
      ;; Prev's here by hooking -queue-render! directly to .forceUpdate. That
      ;; is, Om Prev component state changes will no longer queue and batch.
      ;;
      ;; Note that while this is the app state atom, this is about *component*
      ;; state. Confusing, but Om Prev coordinates component state changes
      ;; using methods on the *app* state atom, presumably because it's a handy
      ;; central location to track things.
      (specify! legacy-state-atom
        om/IRenderQueue
        (-queue-render! [_ c]
          (.forceUpdate c))
        (-get-queue [_] nil)
        (-empty-queue! [_] nil))

      ;; Make the legacy-state-atom available to Om Prev as the *state*. Om
      ;; would normally do this automatically.
      (binding [om/*state* legacy-state-atom]
        (om/build app/app*
                  (let [{legacy-state :legacy/state
                         route ::route}
                        (om-next/props this)]
                    ;; Assoc the route back in as :navigation-point.
                    (assoc legacy-state :navigation-point route))
                  ;; Make the Om Next shared values available in Om Prev as well.
                  {:shared (assoc (om-next/shared this)
                                  ;; Include the legacy-state-atom for the inputs system.
                                  :_app-state-do-not-use legacy-state-atom
                                  ;; NOMERGE Just for debugging.
                                  :om-next-app-state @(om-next/app-state (om-next/get-reconciler this)))})))))

(defn ^:export setup! []
  (support/enable-one!)
  (let [state (initial-state)
        comms {:controls (chan)
               :api (chan)
               :errors (chan)
               :nav (chan)
               :ws (chan)}
        top-level-node (find-top-level-node)
        container (find-app-container)
        history-imp (history/new-history-imp top-level-node)
        pusher-imp (pusher/new-pusher-instance (config/pusher))
        r (om-next/reconciler {:state {:legacy/state state}
                               :parser parser
                               :shared {:comms comms
                                        :timer-atom (timer/initialize)
                                        ;; NOMERGE Analytics is going to be
                                        ;; tricky, as we'll be moving all the
                                        ;; data around. For spike purposes, just
                                        ;; disable it
                                        :track-event (constantly nil)}})

        ;; The legacy-state-atom is a LensedAtom which we can treat like a
        ;; normal atom, but which presents only the legacy state.
        legacy-state-atom (LensedAtom. (om-next/app-state r) [:legacy/state])]

    (set! reconciler r)

    (browser-settings/setup! legacy-state-atom)

    (routes/define-routes! (:current-user state) (:nav comms))

    (om-next/add-root! reconciler
                       Root (goog.dom/getElement "app"))

    (when config/client-dev?
      ;; Re-render when Figwheel reloads.
      (gevents/listen js/document.body
                      "figwheel.js-reload"
                      #(.forceUpdate (om-next/class->any reconciler Root))))

    (go
      (while true
        (alt!
          (:controls comms) ([v] (controls-handler v legacy-state-atom container comms))

          (:nav comms)
          ([[navigation-point _ :as v]]
           ;; :navigate! is a fake navigation point that's actually an
           ;; instruction to navigate to a URL. Navigating to the URL then
           ;; causes a second message on the :nav channel from the history
           ;; implementation, in reaction to which we actually route. Therefore,
           ;; we want to skip the :navigate! message.
           (when-not (= navigation-point :navigate!)
             ;; Essentially compassus.core/set-route! with queue? always true.
             (om-next/transact! reconciler (into `[(set-route! {:route ~navigation-point})]
                                                 (om-next/transform-reads reconciler [::route-data]))))
           (nav-handler v legacy-state-atom history-imp comms))

          (:api comms) ([v] (api-handler v legacy-state-atom container comms))
          (:ws comms) ([v] (ws-handler v legacy-state-atom pusher-imp comms))
          (:errors comms) ([v] (errors-handler v legacy-state-atom container comms)))))

    (when (config/enterprise?)
      (api/get-enterprise-site-status (:api comms)))

    (if-let [error-status (get-in state [:render-context :status])]
      ;; error codes from the server get passed as :status in the render-context
      (put! (:nav comms) [:error {:status error-status}])
      (routes/dispatch! (str "/" (.getToken history-imp))))
    (when-let [user (:current-user state)]
      (analytics/track {:event-type :init-user
                        :current-state state})
      (subscribe-to-user-channel user (:ws comms)))))


;; NOMERGE These will transact on the (globally available) reconciler. Until we
;; get mutations going, let's just disable them.

;; (defn ^:export toggle-admin []
;;   (swap! state/debug-state update-in [:current-user :admin] not))

;; (defn ^:export toggle-dev-admin []
;;   (swap! state/debug-state update-in [:current-user :dev-admin] not))

(defn ^:export explode []
  (swallow-errors
   (assoc [] :deliberate :exception)))


;; Figwheel offers an event when JS is reloaded, but not when CSS is reloaded. A
;; PR is waiting to add this; until then, fire that event from here.
;; See: https://github.com/bhauman/lein-figwheel/pull/463
(defn handle-css-reload [files]
  (figwheel.client.utils/dispatch-custom-event "figwheel.css-reload" files))
