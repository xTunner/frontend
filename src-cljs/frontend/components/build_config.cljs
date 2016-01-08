(ns frontend.components.build-config
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [clojure.string :as string])
    (:require-macros [frontend.utils :refer [html]]))

(defn- maybe-add-start-end
  "Workaround around missing start/end data from backend"
  [error]
  (if (:start error)
    error
    (assoc error
           :start {:line 0 :index 0 :column 0}
           :end {:line 0 :index 0 :column 0})))

(defn diagnostics [config owner]
  (reify
    om/IInitState
    (init-state [_]
      ;; TODO convert CI.inner.Diagnostics to clojurescript
      {:configDiagnostics (js/CI.inner.Diagnostics. (:string config) (->> config :errors (map maybe-add-start-end) clj->js))})
    om/IRenderState
    (render-state [_ {:keys [configDiagnostics]}]
      (html
       [:section
        [:table.code
         [:thead [:tr [:th] [:th]]]
         [:tbody
          (for [line (aget configDiagnostics "lines")]
            (list [:tr {:class (when (aget line "has_errors") "error")}
                   [:td.line-number (aget line "line")]
                   [:td.line
                    (for [piece (aget line "pieces")]
                      (list
                       (when (aget piece "data")
                         [:span {:class (when (aget piece "error") "error")}
                          (aget piece "data")])
                       (when (aget piece "error_flag")
                         [:span [:a.error-flag {:on-click #(do ((aget piece "select"))
                                                               (om/refresh! owner))
                                                :class (when ((aget piece "get_selected")) "selected")}
                                 (inc (aget piece "number"))]])))]]
                  (for [error (aget line "errors")]
                    [:tr.error-message {:class (when ((aget error "get_selected")) "opened")}
                     [:td.line-number]
                     [:td.error-message
                      (when (aget error "path")
                        [:span.path (aget error "path")])
                      (aget error "message") "."
                      [:div.next-button
                       [:a {:on-click #(do ((aget error "select_next"))
                                           (om/refresh! owner))}
                        [:i.fa.fa-angle-right]]]]])))]]]))))

(defn config-errors [build owner]
  (reify
    om/IRender
    (render [_]
      (let [config (:circle_yml build)]
        (html
         [:div.config-diagnostics.heroic
          (when-not (:lethal config)
            [:button.dismiss {:on-click #(raise! owner [:dismiss-config-errors])}
             "Dismiss "
             [:i.fa.fa-times-circle]])
          [:header
           [:div.head-left
            (if (:lethal config)
              (common/icon {:name "fail" :type "status"})
              [:i.error.fa.fa-exclamation-triangle])]
           [:div.head-right
            [:h2 "Dang."]
            [:p
             "We spotted some issues with your " [:code "circle.yml"] "."

             (when-not (:lethal config)
               (if (:failed build)
                 " These may be causing your build to fail! We recommend that you fix them as soon as possible."
                 " These may lead to unexpected behavior and may cause your build to fail soon. We recommend that you fix them as soon as possible."))]
            [:p
             "You may want to look at "
             [:a {:href "/docs/configuration"} "our docs"]
             " or " (common/contact-us-inner owner) " if you're having trouble."]]]
          (om/build diagnostics config)])))))

(defn config-error-snippet [{:keys [error config-string]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [node (om/get-node owner)
            highlight-target (goog.dom.getElementByClass "error-snippet" node)]
        (js/Prism.highlightElement highlight-target)))
    om/IRender
    (render [_]
      (let [lines (string/split-lines config-string)
            start-line (get-in error [:start :line])
            end-line (get-in error [:end :line])
            line-count (inc (- end-line start-line))
            snippet (->> lines
                         (drop start-line)
                         (take line-count)
                         (string/join "\n"))]
        (html
         [:pre.line-numbers {:data-start (inc start-line)}
          [:code.error-snippet.language-yaml
           snippet]])))))

(defn config-error [{:keys [error] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:li
        (string/join "." (:path error))
        " "
        (:message error)
        (om/build config-error-snippet data)]))))

(defn config-errors-v2 [build owner]
  (reify
    om/IRender
    (render [_]
      (let [config (:circle_yml build)
            config-string (:string config)
            errors (:errors config)]
        (html
         [:div.config-errors
          [:div.alert.alert-danger.expanded
           [:div.alert-header
            [:img.alert-icon {:src (common/icon-path "Info-Error")}]
            (str "CIRCLE.YML - " (count errors) " WARNINGS")]
           [:div.alert-body
            [:div.dang
             "Dang! We spotted something wrong with your "
             [:span.circle-yml-highlight
              "circle.yml"]
             ". "
             "These may be causing your builds to fail. "
             "We recommend that you fix them as soon as possible. "
             "You may want to look at our docs or contact us if you’re having trouble."]
            [:ol
             (for [error errors]
               (om/build config-error {:error error
                                       :config-string config-string}))]

            ;; [:div.build-tests-list-container
            ;;  [:ol.list-unstyled.build-tests-list
            ;;   (let [file-failures (->> (group-by :file failures)
            ;;                            (map (fn [[k v]] [k (sort-by test-model/format-test-name v)]))
            ;;                            (into (sorted-map)))
            ;;         [top-map bottom-map] (utils/split-map-values-at file-failures initial-test-render-count)]
            ;;     (list
            ;;      (om/build-all build-tests-file-block top-map)
            ;;      (when-not (empty? bottom-map)
            ;;        (list
            ;;         [:hr]
            ;;         [:li
            ;;          [:a.build-tests-toggle {:on-click #(om/update-state! owner [:is-open?] not)}
            ;;           [:span
            ;;            [:i.fa.build-tests-toggle-icon {:class (if (om/get-state owner :is-open?) "expanded")}]
            ;;            (if (om/get-state owner :is-open?)
            ;;              "Less"
            ;;              "More")]]]
            ;;         (when (om/get-state owner :is-open?)
            ;;           (om/build-all build-tests-file-block bottom-map))))))]]
            ]
           ]



          ;; [:div
          ;;  "Dang! We spotted something wrong with your "
          ;;  [:span.circle-yml-highlight
          ;;   "circle.yml"]
          ;;  ". "
          ;;  "These may be causing your builds to fail. "
          ;;  "We recommend that you fix them as soon as possible. "
          ;;  "You may want to look at our docs or contact us if you’re having trouble."
          ;;  ;; "Your build ran "
          ;;  ;; [:strong (count tests)]
          ;;  ;; " tests in " (string/join ", " (map test-model/pretty-source (keys source-hash))) " with "
          ;;  ;; [:strong "0 failures"]
          ;;  ;; (when slowest
          ;;  ;;   [:div.build-tests-summary
          ;;  ;;    [:p
          ;;  ;;     [:strong "Slowest test:"]
          ;;  ;;     (gstring/format
          ;;  ;;      " %s %s (took %.2f seconds)."
          ;;  ;;      (:classname slowest)
          ;;  ;;      (:name slowest)
          ;;  ;;      (:run_time slowest))]])
          ;;  ]


          ;; [:header
          ;;  [:div.head-left
          ;;   (if (:lethal config)
          ;;     (common/icon {:name "fail" :type "status"})
          ;;     [:i.error.fa.fa-exclamation-triangle])]
          ;;  [:div.head-right
          ;;   [:h2 "Dang."]
          ;;   [:p
          ;;    "We spotted some issues with your " [:code "circle.yml"] "."

          ;;    (when-not (:lethal config)
          ;;      (if (:failed build)
          ;;        " These may be causing your build to fail! We recommend that you fix them as soon as possible."
          ;;        " These may lead to unexpected behavior and may cause your build to fail soon. We recommend that you fix them as soon as possible."))]
          ;;   [:p
          ;;    "You may want to look at "
          ;;    [:a {:href "/docs/configuration"} "our docs"]
          ;;    " or " (common/contact-us-inner owner) " if you're having trouble."]]]
          ;; (om/build diagnostics config)
          ])))))
