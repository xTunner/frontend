(ns frontend.controllers.ws
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :refer [mlog]]
            [frontend.models.build :as build-model]
            [frontend.pusher :as pusher]))

(defmulti ws-event
  (fn [pusher-imp message args state] message))

(defmethod ws-event :default
  [pusher-imp message args state]
  (mlog "Unknown ws event: " (pr-str message))
  state)

(defmethod ws-event :build/update
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (update-in state [:current-build] merge (js->clj data :keywordize-keys true)))))
