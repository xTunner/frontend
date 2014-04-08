(ns frontend.utils
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [goog.crypt :as crypt]
            [goog.crypt.Md5 :as md5]
            [goog.Uri]
            [goog.events :as ge]
            [goog.net.EventType :as gevt])
  (:require-macros [frontend.utils :refer (inspect)])
  (:import [goog.net XhrIo]
           [goog.async Deferred]))

(defn mlog [& messages]
  (.apply (.-log js/console) js/console (clj->js messages)))

(defn mwarn [& messages]
  (.apply (.-warn js/console) js/console messages))

(defn merror [& messages]
  (.apply (.-error js/console) js/console messages))

(def parsed-uri
  (goog.Uri. (-> (.-location js/window) (.-href))))

(def initial-query-map
  {:log-channels?    (or (.getParameterValue parsed-uri "log-channels") false)
   :logging-enabled? (= (.getParameterValue parsed-uri "logging-enabled") "true")
   :restore-state?   (= (.getParameterValue parsed-uri "restore-state") "true")})

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

(defn ajax [url method data-string success & [error headers]]
  (let [request (XhrIo.)
        d (goog.async.Deferred.)
        listener-id (ge/listen request gevt/COMPLETE (fn [response]
                                                       (let [target (.-target response)
                                                             data (if (= method "DELETE")
                                                                    nil
                                                                    (.getResponseJson target))]
                                                         (.callback d data))))]

    ;; Setup deferred callbacks
    (.addErrback d  (fn [error] (.log js/console "Error on ajax: " error)))
    (when success (.addCallback d #(success (js->clj % :keywordize-keys true))))
    (when error (.addErrback d error))
    (.addBoth d (fn [value] (ge/unlistenByKey listener-id) (.dispose request)))
    (mlog "Firing request to " url " via " method " and data - : " data-string)

    ;; Fire request
    (.send request url method data-string headers)
    request))

(defn third [coll]
  (nth coll 2 nil))
