(ns frontend.experiments.no-test-intervention
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.modal :as modal]        
            [frontend.models.feature :as feature]
            [frontend.utils.html :refer [open-ext]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- ab-test-treatment []
  (feature/ab-test-treatment :setup-docs-ab-test))

(defn- track-setup-docs-clicked [owner]
  ((om/get-shared owner :track-event) {:event-type :setup-docs-clicked
                                       :properties {:setup-docs-ab-test 
                                                    (ab-test-treatment)}}))

(defn show-intervention? [build]
  (-> build :outcome keyword (= :no_tests)))

(defn show-setup-docs-modal? [build]
  (and (show-intervention? build)
       (-> build :build_num (= 1))))

(defn- setup-docs-link-props [owner]
  (open-ext {:href "https://circleci.com/docs/manually"
             :target "_blank"
             :on-click #(track-setup-docs-clicked owner)}))

(defn setup-docs-banner [{:keys [owner build]}]
  (when (= (ab-test-treatment) :setup-docs-banner)
    (let [content
          [:span 
           (str "We couldn't detect the settings for your project! Please make sure "
                "you have a circle.yml configuration file in place, and check our doc about ")
           [:a (setup-docs-link-props owner)
               "manual build setup"]
           "."]]
      (common/message {:type :warning
                       :content content}))))

(defn setup-docs-modal [owner]
  (when (= (ab-test-treatment) :setup-docs-modal)
    (let [close-fn #(om/set-state! owner :show-setup-docs-modal? false)]
      (modal/modal-dialog
        {:title "We couldnt determine the test settings for your project"
         :body (html
                [:span 
                 (str "We're sorry about that! Please make sure you have a circle.yml "
                      "configuration file in place, and check our doc about ")
                 [:a (setup-docs-link-props owner)
                     "manual build setup"]
                 "."])
         :actions [(button/button {:kind :primary :on-click close-fn} "Got it")]
         :close-fn close-fn}))))