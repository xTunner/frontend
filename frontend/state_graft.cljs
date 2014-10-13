(ns frontend.state-graft
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:import [goog.ui IdGenerator]))

;;; Modification of Om's "local state considered harmful" example:
;;; https://github.com/swannodette/om/blob/master/examples/harmful/src/core.cljs
;;; The primary departure from the original design is to use the internal
;;; React.js ID, which is component-stable, unlike counter-based fresh IDs.

(defn react-id [x]
  (let [id (.-_rootNodeID x)]
    (assert id)
    id))

(defn get-gstate [owner]
  (aget (.-props owner) "__om_app_state"))

(defn merge-pending-state [owner]
  (let [gstate (get-gstate owner)
        spath  [:state-map (react-id owner)]
        states (get-in @gstate spath)]
    (when (:pending-state states)
      (swap! gstate update-in spath
        (fn [states]
          (-> states
            (assoc :render-state
              (merge (:render-state states) (:pending-state states)))
            (dissoc :pending-state)))))))

(def no-local-state-meths
  (assoc om/pure-methods
    :getInitialState
    (fn []
      (this-as this
        (let [c      (om/children this)
              props  (.-props this)
              istate (or (aget props "__om_init_state") {})
              om-id  (or (::om/id istate)
                         (.getNextUniqueId (.getInstance IdGenerator)))
              state  (merge (dissoc istate ::om/id)
                       (when (satisfies? om/IInitState c)
                         (om/allow-reads (om/init-state c))))
              spath  [:state-map (react-id this) :render-state]]
          (aset props "__om_init_state" nil)
          (swap! (get-gstate this) assoc-in spath state)
          #js {:__om_id om-id})))
    :componentWillMount
    (fn []
      (this-as this
        (om/merge-props-state this)
        (let [c (om/children this)]
          (when (satisfies? om/IWillMount c)
            (om/allow-reads (om/will-mount c))))
        (merge-pending-state this)))
    :componentWillUnmount
    (fn []
      (this-as this
        (let [c     (om/children this)
              spath [:state-map (react-id this)]]
          (when (satisfies? om/IWillUnmount c)
            (om/allow-reads (om/will-unmount c)))
          (swap! (get-gstate this) update-in spath dissoc))))
   :componentWillUpdate
   (fn [next-props next-state]
     (this-as this
       (let [props  (.-props this)
             c      (om/children this)]
         (when (satisfies? om/IWillUpdate c)
           (let [state (.-state this)]
             (om/allow-reads
               (om/will-update c
                 (om/get-props #js {:props next-props})
                 (om/-get-state this))))))
       (merge-pending-state this)))
    :componentDidUpdate
    (fn [prev-props prev-state]
      (this-as this
        (let [c      (om/children this)
              gstate (get-gstate this)
              states (get-in @gstate [:state-map (react-id this)])
              spath  [:state-map (react-id this)]]
          (when (satisfies? om/IDidUpdate c)
            (let [state (.-state this)]
              (om/allow-reads
                (om/did-update c
                  (om/get-props #js {:props prev-props})
                  (or (:previous-state states)
                      (:render-state states))))))
          (when (:previous-state states)
            (swap! gstate update-in spath dissoc :previous-state)))))))

(def no-local
  (specify! (clj->js no-local-state-meths)
    om/ISetState
    (-set-state!
      ([this val render]
         (om/allow-reads
           (let [props     (.-props this)
                 app-state (aget props "__om_app_state")
                 spath  [:state-map (react-id this) :pending-state]]
             (swap! (get-gstate this) assoc-in spath val)
             (when (and (not (nil? app-state)) render)
               (om/-queue-render! app-state this)))))
      ([this ks val render]
         (om/allow-reads
           (let [props     (.-props this)
                 app-state (aget props "__om_app_state")
                 spath  [:state-map (react-id this) :pending-state]]
             (swap! (get-gstate this) update-in spath assoc-in ks val)
             (when (and (not (nil? app-state)) render)
               (om/-queue-render! app-state this))))))
    om/IGetRenderState
    (-get-render-state
      ([this]
         (let [spath [:state-map (react-id this) :render-state]]
           (get-in @(get-gstate this) spath)))
      ([this ks]
         (get-in (om/-get-render-state this) ks)))
    om/IGetState
    (-get-state
      ([this]
         (let [spath  [:state-map (react-id this)]
               states (get-in @(get-gstate this) spath)]
           (or (:pending-state states)
               (:render-state states))))
      ([this ks]
         (get-in (om/-get-state this) ks)))))
