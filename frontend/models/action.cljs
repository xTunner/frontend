(ns frontend.models.action
  (:require [frontend.datetime :as datetime]
            [frontend.models.project :as proj]
            [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]
            goog.string.format))

;; XXX: write an id function
(defn id [action]
  )

(defn failed? [action]
  (#{"failed" "timedout" "cancelled" "infrastructure_fail"} (:status action)))

(defn has-content? [action]
  (or (:has_output action)
      (:bash_command action)
      (:output action)))

(defn duration [{:keys [start_time stop_time] :as action}]
  (cond (:run_time_millis action) (datetime/as-duration (:run_time_millis action))
        (:start_time action) (datetime/as-duration (- (.getTime (js/Date.))
                                                      (js/Date.parse start_time)))
        :else nil))

(defn new-converter [action type]
  (let [default-color (if (= :err type) "red" "brblue")
        starting-state (clj->js (get-in action [:converters-state type]))]
    (js/CI.terminal.ansiToHtmlConverter default-color "brblack" starting-state)))

(defn format-output [action output-index]
  (let [output (get-in action [:output output-index])
        converter (new-converter action (keyword (:type output)))]
    (-> action
        (assoc-in [:output output-index :converted-message] (.append converter (:message output)))
        (assoc-in [:output output-index :react-key] (utils/uuid))
        (assoc-in [:converters-state (keyword (:type output))] (js->clj (.currentState converter) :keywordize-keys true)))))

(defn format-latest-output [action]
  (if-let [output (seq (:output action))]
    (format-output action (dec (count output)))
    action))

(defn format-all-output [action]
  (if-let [output (seq (:output action))]
    (reduce format-output action (range (count output)))
    action))

(defn trailing-output [converters-state]
  (str (get-in converters-state [:out :trailing_out])
       (get-in converters-state [:err :trailing_out])))
