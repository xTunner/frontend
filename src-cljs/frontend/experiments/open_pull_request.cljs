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
       (button/link {:href (build-model/new-pull-request-url build)
                     :data-external true
                     :target "_blank"
                     :kind (if (= "success" (:outcome build))
                             :primary
                             :secondary)
                     :size :medium
                     :on-click #((om/get-shared owner :track-event) {:event-type :open-pull-request-clicked
                                                                     :properties {:branch (:branch build)
                                                                                  :build-outcome (:outcome build)}})}
                    "Open a Pull Request")))))
