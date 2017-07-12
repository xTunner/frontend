(ns frontend.controllers.api
  "Public fns for interacting with api package controllers."
  (:require frontend.controllers.api.contexts
            [frontend.controllers.api.impl :as impl]
            frontend.controllers.api.legacy))

(defn api-event
  [target message status args state]
  (impl/api-event target message status args state))

(defn post-api-event!
  [target message status args previous-state current-state comms]
  (impl/post-api-event! target message status args previous-state current-state comms))
