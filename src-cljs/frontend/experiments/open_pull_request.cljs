(ns frontend.experiments.open-pull-request
  (:require [frontend.models.build :as build-model]
            [frontend.utils.html :refer [open-ext]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- open-pull-request-action [{:keys [build]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :open-pull-request-impression
                                           :properties {:branch (:branch build)
                                                        :build-outcome (:outcome build)}}))

    om/IRender
    (render [_]
      (html
       [:div.open-pull-request-container
        [:a.exception.btn (open-ext {:href (build-model/new-pull-request-url build)
                                     :target "_blank"
                                     :on-click #((om/get-shared owner :track-event) {:event-type :open-pull-request-clicked
                                                                                     :properties {:branch (:branch build)
                                                                                                  :build-outcome (:outcome build)}})})
         [:i.octicon.octicon-git-pull-request.open-pull-request-icon]
         [:span.open-pull-request-text
          "Open a Pull Request"]]]))))
