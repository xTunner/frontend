(ns frontend.instrumentation
  (:require [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]))

(defn wrap-instrumentation [handler api-resp]
  (fn [state]
    (let [state (handler state)]
      (try
        (if-let [request-data (meta api-resp)]
          (let [{:keys [url method request-time response-headers]} request-data]
            (update-in state state/instrumentation-path conj {:url url
                                                              :route (get response-headers "X-Route")
                                                              :method method
                                                              :request-time request-time
                                                              :circle-latency (js/parseInt (get response-headers "X-Circleci-Latency"))
                                                              :query-count (js/parseInt (get response-headers "X-Circleci-Query-Count"))
                                                              :query-latency (js/parseInt (get response-headers "X-Circleci-Query-Latency"))}))
          state)
        (catch :default e
          (utils/merror e)
          state)))))
