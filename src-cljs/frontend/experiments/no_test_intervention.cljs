(ns frontend.experiments.no-test-intervention
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
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

(defn setup-docs-banner [{:keys [owner build]}]
  (let [content
        [:span
         "It looks like we couldn't infer test settings for your project. Refer to our \""
         [:a (setup-docs-props owner)
             "Setting your build up manually"]
         "\" document to get started. It should only take a few minutes."]]
    (common/message {:type :warning
                     :content content})))

(defn setup-docs-modal [owner]
  (let [close-fn #(om/set-state! owner :show-setup-docs-modal? false)]
    (modal/modal-dialog
      {:title "We couldnt determine the test settings for your project"
       :body (html
              [:span 
               (str "We're sorry about that! Please make sure you have a circle.yml "
                    "configuration file in place, and check our doc about ")
               [:a (setup-docs-props owner)
                   "manual build setup"]
               "."])
       :actions [(button/button {:on-click close-fn} "Cancel")
                 (button/link {:kind :primary} "Read doc")]
       :close-fn close-fn})))