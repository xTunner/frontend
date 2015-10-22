(ns frontend.components.build-steps
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.models.action :as action-model]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.components.common :as common]
            [frontend.state :as state]
            [frontend.disposable :as disposable :refer [dispose]]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [goog.events]
            [goog.string :as gstring]
            goog.dom
            goog.style
            goog.string.format
            goog.fx.dom.Scroll
            goog.fx.easing)
  (:require-macros [frontend.utils :refer [html]]))

(defn source-type [source]
  (condp = source
    "db" "UI"
    "template" "standard"
    source))

(defn source-title [source]
  (condp = source
    "template" "Circle generated this command automatically"
    "cache" "Circle caches some subdirectories to significantly speed up your tests"
    "config" "You specified this command in your circle.yml file"
    "inference" "Circle inferred this command from your source code and directory layout"
    "db" "You specified this command on the project settings page"
    "Unknown source"))

(defn output [out owner]
  (reify
    om/IRender
    (render [_]
      (let [message-html (:converted-message out)]
        (html
         [:span.pre {:dangerouslySetInnerHTML
                     #js {"__html" message-html}}])))))

(defn trailing-output [converters-state owner]
  (reify
    om/IRender
    (render [_]
      (let [trailing-out (action-model/trailing-output converters-state)]
        (html
         [:span {:dangerouslySetInnerHTML
                 #js {"__html" trailing-out}}])))))

(defn scroll-to-bottom [element]
  (.scrollIntoView element false))

(defn action [action owner {:keys [uses-parallelism?] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [visible? (action-model/visible? action)
            header-classes  (concat [(:status action)]
                                    (when-not visible?
                                      ["minimize"])
                                    (when (action-model/has-content? action)
                                      ["contents"])
                                    (when (action-model/failed? action)
                                      ["failed"]))]
        (html
         [:div {:class (str "type-" (or (:type action) "none"))}
          [:div.type-divider
           [:span (:type action)]]
          [:div.build-output
           [:div.action_header {:class header-classes}
            [:div.ah_wrapper
             [:div.header {:class (when (action-model/has-content? action)
                                    header-classes)
                           ;; TODO: figure out what to put here
                           :on-click #(raise! owner [:action-log-output-toggled
                                                     {:index (:index @action)
                                                      :step (:step @action)
                                                      :value (not visible?)}])}
              [:div.button {:class (when (action-model/has-content? action)
                                     header-classes)}
               (when (action-model/has-content? action)
                 [:i.fa.fa-chevron-down])]
              [:div.command {:class header-classes}
               [:span.command-text {:title (:bash_command action)}
                (str (when (= (:bash_command action)
                              (:name action))
                       "$ ")
                     (:name action)
                     (when (and uses-parallelism? (:parallel action))
                       (gstring/format " (%s)" (:index action))))]
               [:span.time {:title (str (:start_time action) " to "
                                        (:end_time action))}
                (om/build common/updating-duration {:start (:start_time action)
                                                    :stop (:end_time action)})
                (when (:timedout action) " (timed out)")]
               [:span.action-source
                [:span.action-source-inner {:title (source-title (:source action))}
                 (source-type (:source action))]]]]
             [:div.detail-wrapper
              (when (and visible? (action-model/has-content? action))
                [:div.detail {:class header-classes}
                 (if (and (:has_output action)
                          (nil? (:output action)))
                   [:div.loading-spinner common/spinner]

                   [:div.action-log-messages
                    (common/messages (:messages action))
                    [:i.click-to-scroll.fa.fa-arrow-circle-o-down.pull-right
                     {:on-click #(scroll-to-bottom (.-parentNode (.-currentTarget %)))}]

                    (when (:bash_command action)
                      [:span
                       (when (:exit_code action)
                         [:span.exit-code.pull-right
                          (str "Exit code: " (:exit_code action))])
                       [:pre.bash-command
                        {:title "The full bash command used to run this setup"}
                        (:bash_command action)]])
                    [:pre.output.solarized {:style {:white-space "normal"}}
                     (when (:truncated action)
                       [:span.truncated "(this output has been truncated)"])
                     (om/build-all output (:output action) {:key :react-key})

                     (om/build trailing-output (:converters-state action))

                     (when (:truncated action)
                       [:span.truncated "(this output has been truncated)"])]])])]]]]])))))

(defn container-view [{:keys [container non-parallel-actions]} owner {:keys [uses-parallelism?] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [container-id (container-model/id container)
            actions (remove :filler-action
                            (map (fn [action]
                                   (get non-parallel-actions (:step action) action))
                                 (:actions container)))]
        (html
         [:div.container-view {:style {:left (str (* 100 (:index container)) "%")}
                               :id (str "container_" (:index container))}
          (om/build-all action actions {:key :step
                                        :opts opts})])))))

(defn mount-browser-resize
  "Handles scrolling the container on the build page to the correct position when
  the size of the browser window chagnes. Has to add an event listener at the top level."
  [owner]
  (om/set-state! owner [:browser-resize-key]
                 (disposable/register
                   (goog.events/listen
                     js/window
                     "resize"
                     #(raise! owner
                            ;; This is pretty hacky, it would be nice if we had a better way to do this
                            [:container-selected {:container-id (get-in @(om/get-shared owner [:_app-state-do-not-use]) state/current-container-path)
                                                  :animate? false}]))
                   goog.events/unlistenByKey)))

(defn check-autoscroll [owner deltaY]
  (cond

   ;; autoscrolling and scroll up? That means stop autoscrolling.
   (and (neg? deltaY)
        (om/get-state owner [:autoscroll?]))
   (om/set-state! owner [:autoscroll?] false)

   ;; not autoscrolling and scroll down? If they scrolled all of the way down, better autoscroll
   (and (pos? deltaY)
        (not (om/get-state owner [:autoscroll?])))
   (let [container (om/get-node owner)]
     (when (> (.-height (goog.dom/getViewportSize))
              (.-bottom (.getBoundingClientRect container)))
       (om/set-state! owner [:autoscroll?] true)))

   :else nil))

(defn container-build-steps [{:keys [containers current-container-id]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:autoscroll? false})
    om/IDidMount
    (did-mount [_]
      (mount-browser-resize owner))
    om/IWillUnmount
    (will-unmount [_]
      (dispose (om/get-state owner [:browser-resize-key])))
    om/IDidUpdate
    (did-update [_ _ _]
      (when (om/get-state owner [:autoscroll?])
        (scroll-to-bottom (.querySelector js/document ".main-foot"))))
    om/IRender
    (render [_]
      (let [non-parallel-actions (->> containers
                                      first
                                      :actions
                                      (remove :parallel)
                                      (map (fn [action]
                                             [(:step action) action]))
                                      (into {}))]
        (html
         [:div#container_scroll_parent ;; hides horizontal scrollbar
          [:div#container_parent {:on-wheel (fn [e]
                                              (check-autoscroll owner (aget e "deltaY"))
                                              (when (not= 0 (aget e "deltaX"))
                                                (.preventDefault e)
                                                (let [body (.-body js/document)]
                                                  (set! (.-scrollTop body) (+ (.-scrollTop body) (aget e "deltaY"))))))
                                  :on-scroll (fn [e]
                                               ;; prevent handling scrolling if we're animating the
                                               ;; transition to a new selected container
                                               (let [scroller (.. e -target -scroll_handler)]
                                                 (when (or (not scroller) (.isStopped scroller))
                                                   (raise! owner [:container-parent-scroll]))))
                                  :class (str "selected_" current-container-id)}
           (for [container containers]
             (om/build container-view
                       {:container container
                        :non-parallel-actions non-parallel-actions}
                       {:opts {:uses-parallelism? (< 1 (count containers))}}))]])))))

(defn output-v2 [out owner]
  (reify
    om/IRender
    (render [_]
      (let [message-html (:converted-message out)]
        (html
         [:span.pre {:dangerouslySetInnerHTML
                     #js {"__html" message-html}}])))))

(defn trailing-output-v2 [converters-state owner]
  (reify
    om/IRender
    (render [_]
      (let [trailing-out (action-model/trailing-output converters-state)]
        (html
          [:span {:dangerouslySetInnerHTML
                  #js {"__html" trailing-out}}])))))

(defn action-v2 [action owner {:keys [uses-parallelism?] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [visible? (action-model/visible? action)
            header-classes  (concat [(:status action)]
                                    (when-not visible?
                                      ["minimize"])
                                    (when (action-model/has-content? action)
                                      ["contents"])
                                    (when (action-model/failed? action)
                                      ["failed"]))]
        (html
         [:div {:class (str "type-" (or (:type action) "none"))}
          [:div.type-divider
           [:span (:type action)]]
          [:div.build-output
           [:div.action_header {:class header-classes}
            [:div.ah_wrapper
             [:div.header {:class (when (action-model/has-content? action)
                                    header-classes)
                           ;; TODO: figure out what to put here
                           :on-click #(raise! owner [:action-log-output-toggled
                                                     {:index (:index @action)
                                                      :step (:step @action)
                                                      :value (not visible?)}])}
              [:div.button {:class (when (action-model/has-content? action)
                                     header-classes)}
               (when (action-model/has-content? action)
                 [:i.fa.fa-chevron-down])]
              [:div.command {:class header-classes}
               [:span.command-text {:title (:bash_command action)}
                (str (when (= (:bash_command action)
                              (:name action))
                       "$ ")
                     (:name action)
                     (when (and uses-parallelism? (:parallel action))
                       (gstring/format " (%s)" (:index action))))]
               [:span.time {:title (str (:start_time action) " to "
                                        (:end_time action))}
                (om/build common/updating-duration {:start (:start_time action)
                                                    :stop (:end_time action)})
                (when (:timedout action) " (timed out)")]
               [:span.action-source
                [:span.action-source-inner {:title (source-title (:source action))}
                 (source-type (:source action))]]]]
             [:div.detail-wrapper
              (when (and visible? (action-model/has-content? action))
                [:div.detail {:class header-classes}
                 (if (and (:has_output action)
                          (nil? (:output action)))
                   [:div.loading-spinner common/spinner]

                   [:div.action-log-messages
                    (common/messages (:messages action))
                    [:i.click-to-scroll.fa.fa-arrow-circle-o-down.pull-right
                     {:on-click #(scroll-to-bottom (.-parentNode (.-currentTarget %)))}]
                    (when (:bash_command action)
                      [:span
                       (when (:exit_code action)
                         [:span.exit-code.pull-right
                          (str "Exit code: " (:exit_code action))])
                       [:pre.bash-command
                        {:title "The full bash command used to run this setup"}
                        (:bash_command action)]])
                    [:pre.output.solarized {:style {:white-space "normal"}}
                     (when (:truncated action)
                       [:span.truncated "(this output has been truncated)"])

                     (om/build-all output-v2 (:output action) {:key :react-key})

                     (om/build trailing-output-v2 (:converters-state action))

                     (when (:truncated action)
                       [:span.truncated "(this output has been truncated)"])]])])]]]]])))))

(defn container-view-v2 [{:keys [container non-parallel-actions]} owner {:keys [uses-parallelism?] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [container-id (container-model/id container)
            actions (remove :filler-action
                            (map (fn [action]
                                   (get non-parallel-actions (:step action) action))
                                 (:actions container)))]
        (html
         [:div.container-view {:style {:left (str (* 100 (:index container)) "%")}
                               :id (str "container_" (:index container))}
          (om/build-all action-v2 actions {:key :step
                                           :opts opts})])))))

(defn container-build-steps-v2 [{:keys [containers current-container-id]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:autoscroll? false})
    om/IDidMount
    (did-mount [_]
      (mount-browser-resize owner))
    om/IWillUnmount
    (will-unmount [_]
      (dispose (om/get-state owner [:browser-resize-key])))
    om/IDidUpdate
    (did-update [_ _ _]
      (when (om/get-state owner [:autoscroll?])
        (scroll-to-bottom (.querySelector js/document ".main-foot"))))
    om/IRender
    (render [_]
      (let [non-parallel-actions (->> containers
                                      first
                                      :actions
                                      (remove :parallel)
                                      (map (fn [action]
                                             [(:step action) action]))
                                      (into {}))]
        (html
         [:div#container_scroll_parent ;; hides horizontal scrollbar
          [:div#container_parent {:on-wheel (fn [e]
                                              (check-autoscroll owner (aget e "deltaY"))
                                              (when (not= 0 (aget e "deltaX"))
                                                (.preventDefault e)
                                                (let [body (.-body js/document)]
                                                  (set! (.-scrollTop body) (+ (.-scrollTop body) (aget e "deltaY"))))))
                                  :on-scroll (fn [e]
                                               ;; prevent handling scrolling if we're animating the
                                               ;; transition to a new selected container
                                               (let [scroller (.. e -target -scroll_handler)]
                                                 (when (or (not scroller) (.isStopped scroller))
                                                   (raise! owner [:container-parent-scroll]))))
                                  :class (str "selected_" current-container-id)}
           (for [container containers]
             (om/build container-view-v2
                       {:container container
                        :non-parallel-actions non-parallel-actions}
                       {:opts {:uses-parallelism? (< 1 (count containers))}}))]])))))
