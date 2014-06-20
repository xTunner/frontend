(ns frontend.utils
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [ajax.core :as ajax]
            [cljs-time.core :as time]
            [frontend.env :as env]
            [goog.crypt :as crypt]
            [goog.crypt.Md5 :as md5]
            [goog.Uri]
            [goog.events :as ge]
            [goog.net.EventType :as gevt])
  (:require-macros [frontend.utils :refer (inspect)]))

(defn csrf-token []
  (aget js/window "CSRFToken"))

(def parsed-uri
  (goog.Uri. (-> (.-location js/window) (.-href))))

(defn parse-uri-bool
  "Parses a boolean from a url into true, false, or nil"
  [string]
  (condp = string
    "true" true
    "false" false
    nil))

(def initial-query-map
  {:log-channels? (parse-uri-bool (.getParameterValue parsed-uri "log-channels"))
   :logging-enabled? (parse-uri-bool (.getParameterValue parsed-uri "logging-enabled"))
   :restore-state? (parse-uri-bool (.getParameterValue parsed-uri "restore-state"))
   :rethrow-errors? (parse-uri-bool (.getParameterValue parsed-uri "rethrow-errors"))
   :inspector? (parse-uri-bool (.getParameterValue parsed-uri "inspector"))})

(def logging-enabled?
  (if (nil? (:logging-enabled? initial-query-map))
    (env/development?)
    (:logging-enabled? initial-query-map)))

(defn mlog [& messages]
  (when logging-enabled?
    (.apply (.-log js/console) js/console (clj->js messages))))

(defn mwarn [& messages]
  (when logging-enabled?
    (.apply (.-warn js/console) js/console (clj->js messages))))

(defn merror [& messages]
  (when logging-enabled?
    (.apply (.-error js/console) js/console (clj->js messages))))

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
  (let [str-len (count s)]
    (if (<= str-len (+ length 3))
      s
      (let [over (+ (- str-len length) 3)
            slice-pos (.ceil js/Math (/ (- length 3) 3))]
        (str (subs s 0 slice-pos)
             "..."
             (subs s (+ slice-pos over)))))))

(defn third [coll]
  (nth coll 2 nil))

(defn js->clj-kw
  "Same as js->clj, but keywordizes-keys by default"
  [ds]
  (js->clj ds :keywordize-keys true))

(defn asset-path
  "Returns path of asset in CDN"
  [path]
  (-> js/window
      (aget "renderContext")
      (aget "assetsRoot")
      (str (if (= \/ (first path))
             path
             (str "/" path)))))

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
                                     (merror e)
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
    (ajax/ajax-request url method
                       (ajax/transform-opts
                        {:format (json-response-format {:keywords? keywords? :url url :method method})
                         :response-format response-format
                         :keywords? keywords?
                         :params params
                         :headers (merge {:Accept "application/json"}
                                         (when (re-find #"^/" url)
                                           {:X-CSRFToken (csrf-token)}))
                         :handler #(binding [frontend.async/*uuid* uuid]
                                     (put! channel [message :success (assoc % :context context)]))
                         :error-handler #(binding [frontend.async/*uuid* uuid]
                                           (put! channel [message :failed (assoc % :context context)]))
                         :finally #(binding [frontend.async/*uuid* uuid]
                                     (put! channel [message :finished context]))}))))

(defn edit-input
  "Meant to be used in a react event handler, usually for the :on-change event on input.
  Path is the vector of keys you would pass to assoc-in to change the value in state,
  event is the Synthetic React event. Pulls the value out of the event.
  Optionally takes :value as a keyword arg to override the event's value"
  [controls-ch path event & {:keys [value]
                             :or {value (.. event -target -value)}}]
  (put! controls-ch [:edited-input {:path path :value value}]))

(defn toggle-input
  "Meant to be used in a react event handler, usually for the :on-change event on input.
  Path is the vector of keys you would pass to update-in to toggle the value in state,
  event is the Synthetic React event."
  [controls-ch path event]
  (put! controls-ch [:toggled-input {:path path}]))
