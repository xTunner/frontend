(ns frontend.models.action
  (:require [clojure.string :as string]
            [frontend.datetime :as datetime]
            [frontend.models.feature :as feature]
            [frontend.models.project :as proj]
            [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]
            goog.string.format))

(defn failed? [action]
  (#{"failed" "timedout" "canceled" "infrastructure_fail"} (:status action)))

(defn has-content? [action]
  (or (:has_output action)
      (:bash_command action)
      (:output action)))

(defn visible? [action]
  (get action :show-output (or (not= "success" (:status action))
                               (seq (:messages action)))))

(defn running? [action]
  (and (:start_time action)
       (not (:stop_time action))
       (not (:run_time_millis action))))

(defn duration [{:keys [start_time stop_time] :as action}]
  (cond (:run_time_millis action) (datetime/as-duration (:run_time_millis action))
        (:start_time action) (datetime/as-duration (- (.getTime (js/Date.))
                                                      (js/Date.parse start_time)))
        :else nil))

(defn new-converter [action type]
  (let [default-color "white"
        starting-state (clj->js (get-in action [:converters-state type]))]
    (js/CI.terminal.ansiToHtmlConverter default-color "brblack" starting-state)))

(defn strip-console-codes
  "Strips console codes if output is over 2mb (assuming 2 bytes per char)"
  [message]
  (string/replace message #"\u001B\[[^A-Za-z]*[A-Za-z]" ""))

(defn format-output [action output-index]
  (let [output (get-in action [:output output-index])
        html-escaped-message (gstring/htmlEscape (:message output))
        should-strip? (< (* 1024 1024) (count html-escaped-message))
        converter (new-converter action (keyword (:type output)))
        message (if should-strip?
                  (strip-console-codes html-escaped-message)
                  (.append converter html-escaped-message))]
    (-> action
        (assoc-in [:output output-index :converted-message] message)
        (assoc-in [:output output-index :react-key] (utils/uuid))
        (assoc-in [:converters-state (keyword (:type output))] (if should-strip?
                                                                 {:color "white"
                                                                  :italic false
                                                                  :bold false
                                                                  :converted-output message}
                                                                 (merge (utils/js->clj-kw (.currentState converter))
                                                                        {:converted-output (.get_trailing converter)}))))))

(defn format-latest-output [action]
  (if-let [output (seq (:output action))]
    (format-output action (dec (count output)))
    action))

(defn format-all-output [action]
  (if-let [output (seq (:output action))]
    (reduce format-output action (range (count output)))
    action))

(defn trailing-output [converters-state]
  (str (get-in converters-state [:out :converted-output])
       (get-in converters-state [:err :converted-output])))


(def max-output-size
  "Limits the number of chacaters we will try to display in the dom.
   The full output is availible for download."
  (* 100 1000))
