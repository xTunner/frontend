(ns frontend.components.pieces.modal
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [goog.events :as gevents]
            om.dom
            om.next)
  (:require-macros [frontend.utils :refer [component element html]]))

(defn dialog
  "A dialog box suitable for presenting in a modal.

  :title    - Text that appears in the header of the dialog.
  :body     - The body of the dialog.
  :actions  - A sequence of action elements, typically buttons, which will appear
              along the bottom of the dialog.
  :close-fn - A function to call when the close button is clicked."
  [{:keys [title body actions close-fn]}]
  (component
    (html
     [:div
      [:.header
       [:.modal-component-title title]
       [:i.material-icons.close-button {:on-click close-fn} "clear"]]
      [:.body body]
      (when (seq actions)
        [:.actions
         (for [action actions]
           [:.action action])])])))

(defn modal
  "A modal presentation. The given content will be displayed centered over a
  darkened background.

  :shown?   - When true, the modal is displayed. When false, it isn't. When this
              value changes, the modal will animate in or out.
  :close-fn - A function to call when the background is clicked. Typically
              causes :shown? to become false and the modal to disappear."
  [{:keys [shown? close-fn]} content]
  (component
    (js/React.createElement
     js/React.addons.CSSTransitionGroup
     #js {:transitionName "modal"
          :transitionAppear true
          :transitionAppearTimeout 500
          :transitionEnterTimeout 500
          :transitionLeaveTimeout 500}
     (when shown?
       (element :modal
         (html
          [:div {:on-click #(when (= (.-target %) ( .-currentTarget %))
                              (close-fn))}
           [:.box
            content]]))))))

(defn modal-dialog
  "A dialog displayed in a modal presentation. Takes the props that both
  components take, in a single map; :close-fn is the same for both."
  [opts]
  (modal (select-keys opts [:shown? :close-fn])
         (dialog (select-keys opts [:title :body :actions :close-fn]))))


(dc/do

  ;; Renders its children in an iframe. (This lets us demonstrate a modal
  ;; without taking over the screen.)
  (om.next/defui IFrame
    Object
    (componentDidMount [this]
      (let [outer-doc (.-ownerDocument (om.dom/node this))
            inner-doc (.-contentDocument (om.dom/node this))
            ;; This <div> will be what we ReactDOM.render into. (React doesn't
            ;; like to render directly into a <body>.)
            react-container (.createElement inner-doc "div")]

        (.syncStyleSheets this)

        ;; Watch for figwheel reloading the CSS.
        (set! (.-figwheelHandlerKey this)
              (gevents/listen (.-body outer-doc)
                              "figwheel.css-reload"
                              #(.syncStyleSheets this)))

        ;; Append the container into the body.
        (.appendChild (.-body inner-doc) react-container)
        (set! (.-container this) react-container))
      (.renderToContainer this))

    (componentWillUnmount [this]
      (gevents/unlistenByKey (.-figwheelHandlerKey this)))

    (componentDidUpdate [this _ _]
      (.renderToContainer this))

    (syncStyleSheets [this]
      (let [outer-doc (.-ownerDocument (om.dom/node this))
            inner-doc (.-contentDocument (om.dom/node this))

            old-stylesheet-nodes
            (doall (map #(.-ownerNode %) (array-seq (.-styleSheets inner-doc))))]

        ;; Copy in outer doc's stylesheets.
        (doseq [sheet (array-seq (.-styleSheets outer-doc))]
          (.appendChild (.-head inner-doc)
                        (->> sheet .-ownerNode (.importNode inner-doc))))

        ;; Then remove old stylesheets.
        (doseq [sheet-node old-stylesheet-nodes]
          (.remove sheet-node))))

    (renderToContainer [this]
      (js/ReactDOM.render (html [:div (om.next/children this)])
                          (.-container this)))

    (render [this]
      (html [:iframe {:style {:width "100%"
                              :height (:height (om.next/props this))}}])))

  (def iframe (om.next/factory IFrame))


  (defcard dialog
    (dialog {:title "Are you sure?"
             :body "Are you sure you want to remove the \"Foo\" Apple Code Signing Key?"
             :actions [(button/button {} "Cancel")
                       (button/button {:primary? true} "Delete")]
             :close-fn identity})
    {}
    {:classname "background-gray"})

  (defcard modal
    (fn [state]
      (iframe {:height "300px"}
              [:div {:style {:display "flex"
                             :justify-content "center"
                             :align-items "center"
                             :height "100%"}}
               [:div
                (button/button {:on-click #(swap! state assoc :shown? true)
                                :primary? true}
                               "Show Modal")
                (modal {:shown? (:shown? @state)
                        :close-fn #(swap! state assoc :shown? false)}
                       (html
                        [:div {:style {:width "300px"
                                       :height "200px"
                                       :display "flex"
                                       :justify-content "center"
                                       :align-items "center"}}
                         "Modal Content"]))]]))
    {:shown? false})

  (defcard modal-dialog
    (fn [state]
      (iframe {:height "300px"}
              [:div {:style {:display "flex"
                             :justify-content "center"
                             :align-items "center"
                             :height "100%"}}
               [:div
                (button/button {:on-click #(swap! state assoc :shown? true)
                                :primary? true}
                               "Show Modal")
                (modal-dialog {:shown? (:shown? @state)
                               :title "Are you sure?"
                               :body "Are you sure you want to remove the \"Foo\" Apple Code Signing Key?"
                               :actions [(button/button {} "Cancel")
                                         (button/button {:primary? true} "Delete")]
                               :close-fn #(swap! state assoc :shown? false)})]]))
    {:shown? false}))
