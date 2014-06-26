(ns frontend.utils.ajax
  (:require [ajax.core :as clj-ajax]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [cljs-time.core :as time]
            [frontend.async :refer [put!]]
            [frontend.utils :as utils :include-macros true]))

;; https://github.com/JulianBirch/cljs-ajax/blob/master/src/ajax/core.cljs
;; copy of the default json formatter, but returns a map with json body
;; in :resp and extra request metadata: :response-headers, :url, :method, and :request-time
(defn json-response-format
  "Returns a JSON response format.  Options include
   :keywords? Returns the keys as keywords
   :prefix A prefix that needs to be stripped off.  This is to
   combat JSON hijacking.  If you're using JSON with GET request,
   you should use this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
  ([{:keys [prefix keywords? url method start-time]
     :or {start-time (time/now)}}]
     {:read (fn read-json [xhrio]
              (let [json (.getResponseJson xhrio prefix)
                    headers (js->clj (.getResponseHeaders xhrio))
                    request-time (try
                                   (time/in-millis (time/interval start-time (time/now)))
                                   (catch :default e
                                     (utils/merror e)
                                     0))]
                {:resp (js->clj json :keywordize-keys keywords?)
                 :response-headers headers
                 :url url
                 :method method
                 :request-time request-time}))
      :description (str "JSON"
                        (if prefix (str " prefix '" prefix "'"))
                        (if keywords? " keywordize"))}))

;; XXX only implementing JSON format and not implementing prefixes for now since we don't use either
(defn ajax [method url message channel & {:keys [params keywords? context]
                                          :or {keywords? true}}]
  (let [uuid frontend.async/*uuid*]
    (put! channel [message :started context])
    (clj-ajax/ajax-request url method
                           (clj-ajax/transform-opts
                            {:format (merge (clj-ajax/json-request-format)
                                            (json-response-format {:keywords? keywords? :url url :method method}))
                             :response-format :json
                             :keywords? keywords?
                             :params params
                             :headers (merge {:Accept "application/json"}
                                             (when (re-find #"^/" url)
                                               {:X-CSRFToken (utils/csrf-token)}))
                             :handler #(binding [frontend.async/*uuid* uuid]
                                         (put! channel [message :success (assoc % :context context)]))
                             :error-handler #(binding [frontend.async/*uuid* uuid]
                                               (put! channel [message :failed (assoc % :context context)]))
                             :finally #(binding [frontend.async/*uuid* uuid]
                                         (put! channel [message :finished context]))}))))

(defn managed-ajax [method url & {:keys [params keywords?]
                                  :or {keywords? true}}]
  (let [channel (chan)]
    (clj-ajax/ajax-request url method
                       (clj-ajax/transform-opts
                        {:format (merge (clj-ajax/json-request-format)
                                        (json-response-format {:keywords? keywords? :url url :method method}))
                         :response-format :json
                         :keywords? keywords?
                         :params params
                         :headers (merge {:Accept "application/json"}
                                         (when (re-find #"^/" url)
                                           {:X-CSRFToken (utils/csrf-token)}))
                         :handler #(put! channel (assoc % :status :success))
                         :error-handler #(put! channel %)
                         :finally #(close! channel)}))
    channel))
