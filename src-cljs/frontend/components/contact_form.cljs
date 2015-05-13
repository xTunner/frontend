(ns frontend.components.contact-form
  (:require [clojure.string :as str]
            [frontend.utils.ajax :as ajax]
            [goog.style :as gstyle]
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html]])
  (:import [goog.math Rect]))

(defn contact-form [props children-f]
  (fn [_ owner]
    (reify
      om/IInitState
      (init-state [_]
        {:show-validations? false
         :notice nil
         :loading? false})
      om/IRenderState
      (render-state [_ {:keys [show-validations? notice loading?]}]
        (html
          [:form
           (merge
             props
             {:class (str/join " " (filter identity [(:class props) (when show-validations? "show-validations")]))
              :no-validate true
              :on-submit (fn [e]
                           (.preventDefault e)
                           (let [form (.-target e)
                                 action (.-action form)
                                 params (into {} (map (juxt #(.-name %) #(.-value %)) (.-elements form)))
                                 valid? (fn [f] (every? #(.checkValidity %) (.-elements f)))]
                             (if (not (valid? form))
                               (om/set-state! owner [:show-validations?] true)
                               (do
                                 (om/set-state! owner [:show-validations?] false)
                                 (om/set-state! owner [:loading?] true)
                                 (go (let [resp (<! (ajax/managed-form-post
                                                      action
                                                      :params params))]
                                       (om/set-state! owner [:loading?] false)
                                       (if (= (:status resp) :success)
                                         (do
                                           (om/set-state! owner [:notice] nil)
                                           (.reset form))
                                         (om/set-state! owner [:notice] {:type "error" :message "Sorry! There was an error sending your message."}))))))))})
           (children-f show-validations? notice loading?)])))))

(defn validated-form-control [props owner]
  (let [update-state
        (fn [input]
          (om/set-state! owner {:value (.-value input)
                                :validation-message (let [msg (.-validationMessage input)]
                                                      (when (not= "" msg)
                                                        msg))}))]
    (reify
      om/IInitState
      (init-state [_]
        {:value nil
         :validation-message nil})
      om/IDidUpdate
      (did-update [_ prev-props prev-state]
        (let [container (om/get-node owner "validation-message-container")
              rendered-children (filter #(not= "none" (gstyle/getComputedStyle % "display")) (.-children container))
              client-rects (map #(.getBoundingClientRect %) rendered-children)
              goog-rects (map #(Rect. (.-left %) (.-top %) (.-width %) (.-height %)) client-rects)
              container-client-rect (.getBoundingClientRect container)
              origin-rect (Rect. (.-left container-client-rect) (.-top container-client-rect) 0 0)
              bounding-rect (reduce Rect.boundingRect origin-rect goog-rects)]
          (gstyle/setHeight container (.-height bounding-rect))))
      om/IDidMount
      (did-mount [_]
        ;; Update our state based on the DOM immediately (and later on-change).
        (update-state (om/get-node owner "control")))
      om/IRenderState
      (render-state [_ {:keys [value validation-message]}]
        (html
          [:div.validated-form-control
           [(:constructor props)
            (merge (dissoc props :constructor :show-validations?)
                   {:value value
                    :ref "control"
                    :on-change #(update-state (.-target %))})]
           [:div {:class "validation-message-container" :ref "validation-message-container"}
            (when validation-message
              [:div.validation-message validation-message])]])))))
