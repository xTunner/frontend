(ns frontend.utils
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [put!]]
            [ajax.core :as ajax]
            [cljs-time.core :as time]
            [frontend.env :as env]
            [goog.async.AnimationDelay]
            [goog.crypt :as crypt]
            [goog.crypt.Md5 :as md5]
            [goog.Uri]
            [goog.events :as ge]
            [goog.net.EventType :as gevt]
            [sablono.core :as html :include-macros true])
  (:require-macros [frontend.utils :refer (inspect timing)]))

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
   :inspector? (parse-uri-bool (.getParameterValue parsed-uri "inspector"))
   :render-colors? (parse-uri-bool (.getParameterValue parsed-uri "render-colors"))
   :invited-by (.getParameterValue parsed-uri "invited-by")})

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

(defn md5 [content]
  (let [container (goog.crypt.Md5.)]
    (.update container content)
    (crypt/byteArrayToHex (.digest container))))

(defn email->gravatar-url [email]
  (let [email (or email "unknown-email@unknown-domain.com")
        hash (md5 email)]
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

(defn cdn-path
  "Returns path of asset in CDN"
  [path]
  (-> js/window
      (aget "renderContext")
      (aget "assetsRoot")
      (str (if (= \/ (first path))
             path
             (str "/" path)))))

(defn display-branch [branch]
  (-> branch name js/decodeURIComponent))

(defn encode-branch [branch]
  (-> branch name js/encodeURIComponent))

;; Stores unique keys to uuids for the functions
(def debounce-state (atom {}))

(defn debounce
  "Takes a unique key and a function, will only execute the last function
   in a sliding 20ms interval (slightly longer than 16ms, time for rAF, seems to work best)"
  [unique-key f & {:keys [timeout]
                   :or {timeout 100}}]
  (js/clearTimeout (get @debounce-state unique-key))
  (let [timeout-id (js/setTimeout f timeout)]
    (swap! debounce-state assoc unique-key timeout-id)))

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

;; TODO: get rid of bootstrap modals
(defn open-modal
  "Open bootstrap modal with given selector"
  [selector]
  (mwarn "Please remove the modal on" selector)
  (let [jq (aget js/window "$")
        $node (jq selector)
        $modal (aget $node "modal")]
    (.call $modal $node #js {:open true})))

;; TODO: get rid of bootstrap popovers
(defn popover
  "Sets up a popover given selector and options. Once this is called, the popover
   should work as expected"
  [selector options]
  (mwarn "Please remove the popover on" selector)
  (let [jq (aget js/window "$")
        $node (jq selector)
        $popover (aget $node "popover")]
    (.call $popover $node (clj->js options))))

;; TODO: get rid of bootstrap tooltips
(defn tooltip
  "Sets up a tooltip given selector and options. Once this is called, the tooltip
   should work as expected"
  [selector & [options]]
  (mwarn "Please remove the tooltip on" selector)
  (let [jq (aget js/window "$")
        $node (jq selector)
        $tooltip (aget $node "tooltip")]
    (if options
      (.call $tooltip $node (clj->js options))
      (.call $tooltip $node))))

;; TODO: get rid of bootstrap typeahead
(defn typeahead
  "Sets up typahead given selector and options. Once this is called, typeahead
   should work as expected"
  [selector & [options]]
  (mwarn "Please remove typeahead on" selector)
  (let [jq (aget js/window "$")
        $node (jq selector)
        $typeahead (aget $node "typeahead")]
    (.call $typeahead $node (clj->js options))))

(defn rAF
  "Calls passed in function inside a requestAnimationFrame, falls back to timeouts for
   browers without requestAnimationFrame"
  [f]
  (.start (goog.async.AnimationDelay. f)))

(defn strip-html
  "Strips all html characters from the string"
  [str]
  (string/replace str #"[&<>\"']" ""))
