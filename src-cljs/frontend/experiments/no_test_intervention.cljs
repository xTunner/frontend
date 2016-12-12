(ns frontend.experiments.no-test-intervention
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.modal :as modal]        
            [frontend.models.feature :as feature]
            [frontend.utils.html :refer [open-ext]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn ab-test-treatment []
  (feature/ab-test-treatment :setup-docs-ab-test))

(defn- track-setup-docs-clicked [track-fn]
  (track-fn {:event-type :setup-docs-clicked}))

(defn show-intervention? [build]
  (-> build :outcome keyword (= :no_tests)))

(defn show-setup-docs-modal? [build]
  (and (show-intervention? build)
       (-> build :build_num (= 1))))

(defn- setup-docs-link-props [track-fn]
  (open-ext {:href "https://circleci.com/docs/manually"
             :target "_blank"
             :on-click #(track-setup-docs-clicked track-fn)}))

(defn setup-docs-banner [_ owner]
  (reify
    om/IRender
    (render [_]
      (html
        (let [track-fn (om/get-shared owner :track-event)
              content
              (html
                [:div
                 [:span 
                  (str "We couldn't detect the settings for your project! "
                       "Please make sure you have a circle.yml configuration "
                       "file in place, and check our doc about ")
                  [:a (setup-docs-link-props track-fn)
                      "manual build setup"]
                  "."]])]
          (common/message {:type :warning
                           :content content}))))))

(defn setup-docs-modal [{:keys [close-fn]} owner]
  (reify
    om/IRender
    (render [_]
      (let [track-fn (om/get-shared owner :track-event)]
        (modal/modal-dialog
          {:title "We couldnt determine the test settings for your project"
           :body (html
                   [:span 
                    (str "We're sorry about that! Please make sure you have a "
                         "circle.yml configuration file in place, and check "
                         "our doc about ")
                    [:a (setup-docs-link-props track-fn)
                        "manual build setup"]
                    "."])
           :actions [(button/button {:kind :primary 
                                     :on-click close-fn} 
                                    "Got it")]
           :close-fn close-fn})))))