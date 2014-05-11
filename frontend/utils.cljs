(ns frontend.utils
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [ajax.core :as ajax]
            [goog.crypt :as crypt]
            [goog.crypt.Md5 :as md5]
            [goog.Uri]
            [goog.events :as ge]
            [goog.net.EventType :as gevt])
  (:require-macros [frontend.utils :refer (inspect)]))

(defn csrf-token []
  (aget js/window "CSRFToken"))

(defn mlog [& messages]
  (.apply (.-log js/console) js/console (clj->js messages)))

(defn mwarn [& messages]
  (.apply (.-warn js/console) js/console (clj->js messages)))

(defn merror [& messages]
  (.apply (.-error js/console) js/console (clj->js messages)))

(def parsed-uri
  (goog.Uri. (-> (.-location js/window) (.-href))))

(def initial-query-map
  {:log-channels?    (or (.getParameterValue parsed-uri "log-channels") false)
   :logging-enabled? (= (.getParameterValue parsed-uri "logging-enabled") "true")
   :restore-state?   (= (.getParameterValue parsed-uri "restore-state") "true")
   :rethrow-errors? (= (.getParameterValue parsed-uri "rethrow-errors") "true")})

(defn uuid
  "returns a type 4 random UUID: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx"
  []
  (let [r (repeatedly 30 (fn [] (.toString (rand-int 16) 16)))]
    (apply str (concat (take 8 r) ["-"]
                       (take 4 (drop 8 r)) ["-4"]
                       (take 3 (drop 12 r)) ["-"]
                       [(.toString  (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16)]
                       (take 3 (drop 15 r)) ["-"]
                       (take 12 (drop 18 r))))))

(defn email->gravatar-url [email]
  (let [email (or email "unknown-email@unknown-domain.com")
        container (goog.crypt.Md5.)
        _ (.update container email)
        hash (crypt/byteArrayToHex (.digest container))]
    (str "https://secure.gravatar.com/avatar/" hash "?s=50")))

(defn notify-error [ch message]
  (put! ch [:error-triggered message]))

(defn trim-middle [s length]
  ;; XXX Implement proper middle-trim
  (subs s 0 (min length (count s))))

(defn third [coll]
  (nth coll 2 nil))

(defn ajax [method url message channel & {:keys [params format response-format keywords? context]
                                          :or {format :json
                                               response-format :json
                                               keywords? true}}]
  (put! channel [message :started context])
  (ajax/ajax-request url method
                     (ajax/transform-opts
                      {:format format
                       :response-format response-format
                       :keywords? keywords?
                       :params params
                       :headers (merge {:Accept "application/json"}
                                       (when (re-find #"^/" url)
                                         {:X-CSRFToken (csrf-token)}))
                       :handler #(put! channel [message :success {:resp %
                                                                  :context context}])
                       :error-handler #(put! channel [message :failed {:resp %
                                                                       :context context}])
                       :finally #(put! channel [message :finished context])})))

(defn edit-input [controls-ch path event]
  (put! controls-ch [:edited-input {:path path
                                    :value (.. event -target -value)
                                    :input-name input-name}]))
