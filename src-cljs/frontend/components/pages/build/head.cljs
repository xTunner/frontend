(ns frontend.components.pages.build.head
  (:require [cljs.core.async :as async :refer [chan]]
            [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.components.build-head :as old-build-head]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn build-head [{:keys [build-data project-data workflow-data] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html
         [:div
          (if workflow-data
            (om/build old-build-head/build-head-content-workflow (select-keys data [:build-data :project-data :workflow-data]))
            (om/build old-build-head/build-head-content (select-keys data [:build-data :project-data])))
          [:div.build-sub-head
           (om/build old-build-head/build-sub-head data)]])))))

(dc/do
  (defcard-om build-head
    build-head
    {:build-data {:build {:vcs_url "https://github.com/acme/anvil"
                          :status "success"}}}
    {:shared {:comms {:controls (chan)}
              :track-event (constantly nil)}}))
