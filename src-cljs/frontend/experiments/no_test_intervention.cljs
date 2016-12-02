(ns frontend.experiments.no-test-intervention
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.modal :as modal]        
            [frontend.models.feature :as feature]
            [frontend.utils.html :refer [open-ext]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn track-setup-docs-clicked [owner]
  ((om/get-shared owner :track-event) 
   {:event-type :setup-docs-clicked
                :properties {:setup-docs-ab-test 
                             (feature/ab-test-treatment :setup-docs-ab-test)}}))

(defn show-intervention? [build]
  (-> build :outcome keyword (= :no_tests)))

(defn setup-docs-props [owner]
  (open-ext {:href "https://circleci.com/docs/manually"
             :target "_blank"
             :on-click #(track-setup-docs-clicked owner)}))

(defn setup-docs-banner [owner build]
  (when show-intervention?
    (let [content
          [:span
            "It looks like we couldn't infer test settings for your project. Refer to our \""
            [:a (setup-docs-props owner)
                "Setting your build up manually"]
            "\" document to get started. It should only take a few minutes."]]
      (common/message {:type :warning
                       :content content}))))

