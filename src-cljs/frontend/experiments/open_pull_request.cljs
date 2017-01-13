(ns frontend.experiments.open-pull-request
  (:require [frontend.components.pieces.button :as button]
            [frontend.models.build :as build-model]
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
        (button/link-icon {:href (build-model/new-pull-request-url build)
                           :label "Open a Pull Request"
                           :data-external true
                           :target "_blank"
                           :on-click #((om/get-shared owner :track-event) {:event-type :open-pull-request-clicked
                                                                           :properties {:branch (:branch build)
                                                                                        :build-outcome (:outcome build)}})}
                          [:i.octicon.octicon-git-pull-request])))))
