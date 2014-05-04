(ns frontend.components.build-steps
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.action :as action-model]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.components.common :as common]
            [frontend.utils :as utils]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [goog.string :as gstring]
            goog.string.format
            goog.fx.dom.Scroll
            goog.fx.easing))

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

(defn build-output [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [action (:action data)
            controls-ch (:controls-ch data)
            visible? (get action :show-output (or (not= "success" (:status action))
                                                  (seq (:messages action))))
            header-classes  (concat [(:status action)]
                                    (when-not visible?
                                      ["minimize"])
                                    (when (action-model/has-content? action)
                                      ["contents"])
                                    (when (action-model/failed? action)
                                      ["failed"]))]
        (html
         [:div.build-output
          [:div.action_header {:class header-classes}
           [:div.ah_wrapper
            [:div.header {:class (when (action-model/has-content? action)
                                   header-classes)
                          ;; TODO: figure out what to put here
                          :on-click #(put! controls-ch [:action-log-output-toggled
                                                        {:index (:index @action)
                                                         :step (:step @action)}])}
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
                    (when (:parallel action)
                      (gstring/format " (%s)" (:index action))))]
              [:span.time {:title (str (:start_time action) " to "
                                       (:end_time action))}
               (str (action-model/duration action)
                    (when (:timedout action) " (timed out)"))]
              [:span.action-source
               [:span.action-source-inner {:title (source-title (:source action))}
                (source-type (:source action))]]]]
            [:div.detail-wrapper
             (when (and visible? (action-model/has-content? action))
               [:div.detail {:class header-classes}
                (if (:retrieving-output action)
                  [:div.loading-spinner common/spinner]

                  [:div#action-log-messages
                   ;; XXX click-to-scroll
                   [:i.click-to-scroll.fa.fa-arrow-circle-o-down.pull-right]

                   (common/messages (:messages action))
                   (when (:bash_command action)
                     [:span
                      (when (:exit_code action)
                        [:span.exit-code.pull-right
                         (str "Exit code: " (:exit_code action))])
                      [:pre.bash-command
                       {:title "The full bash comand used to run this setup"}
                       (:bash_command action)]])
                   [:pre.output.solarized {:style {:white-space "normal"}}
                    [:span.pre {:dangerouslySetInnerHTML
                                (clj->js {"__html"
                                          (action-model/format-output action)})}]]])])]]]])))))

(defn container-view [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [container (:container data)
            container-id (container-model/id container)
            build (:build data)
            action-groups (group-by :type (:actions container))
            controls-ch (:controls-ch data)]
        (html
         [:div.container-view {:style {:left (str (* 100 (:index container)) "%")}
                               :class (when (= container-id (get build :current-container-id 0)) "current_container")
                               :id (str "container_" (:index container))}
          (map (fn [[type actions]]
                 (list*
                  [:div.type-divider
                   [:span type]]
                  (map (fn [action]
                         (om/build build-output
                                   {:action action
                                    :controls-ch controls-ch}
                                   {:opts opts}))
                       actions)))
               action-groups)])))))

(defn container-build-steps [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:build data)
            containers (:containers data)
            controls-ch (:controls-ch data)]
        (html
         [:div#container_scroll_parent ;; hides horizontal scrollbar
          [:div#container_parent {:on-wheel (fn [e]
                                              (when (not= 0 (.-deltaX e))
                                                (.preventDefault e)
                                                (aset js/document.body "scrollTop" (+ js/document.body.scrollTop (.-deltaY e)))))
                                  :on-scroll #(put! controls-ch [:container-parent-scroll])
                                  :scroll "handle_browser_scroll"
                                  :window-resize "realign_container_viewport"
                                  :resize-sensor "height_changed"}
           ;; XXX handle scrolling and resize sensor
           ;; probably have to replace resize sensor with something else
           (map (fn [container] (om/build container-view
                                          {:container container
                                           :build build
                                           :controls-ch controls-ch}
                                          {:opts opts}))
                containers)]])))))
