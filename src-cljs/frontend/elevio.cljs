(ns frontend.elevio
  (:require [frontend.utils :as utils]
            [goog.dom :as gdom]
            [goog.dom.classlist :as class-list]))

(def account-id "5639122987b91")

(defn open-module-fn []
  (some-> (aget js/window "_elev")
          (aget "openModule")))

(defn broken?
  "Returns true if elevio is broken, false otherwise"
  []
  (nil? (open-module-fn)))

(defn open-module! [module-name]
  (when-not (broken?)
    ((open-module-fn) module-name)))

(defn show-support! []
  (open-module! "support"))

(defn show-status! []
  (open-module! "status"))

(defn get-root []
  (gdom/getElement "elevio-widget"))

(defn disable! []
  (when-let [el (get-root)]
    (gdom/removeNode el))
  (aset js/window "_elev" #js {}))

(defn enable! []
  (class-list/add js/document.body "circle-elevio")
  (aset js/window "_elev" (or (aget js/window "_elev") #js {}))
  (let [set-elev! (partial aset (aget js/window "_elev"))
        is-free (boolean (some-> js/window
                                 (aget "ldUser")
                                 (aget "custom")
                                 (aget "free")))
        user-info (aget js/window "elevSettings")
        support-module-id "2968"
        discuss-link-module-id "3003"
        discuss-support-link-module-id "3762"]
    (if is-free
      ;; disable support module, discuss link module
      (set-elev! "disabledModules" #js [support-module-id discuss-link-module-id])
      ;; else disable discuss support link module
      (set-elev! "disabledModules" #js [discuss-support-link-module-id]))
    (set-elev! "account_id" account-id)
    (set-elev! "user" user-info)
    (set-elev! "pushin" "false")
    (set-elev! "translations"
          #js {"loading"
               #js {"loading_ticket" "Loading support request"
                    "loading_tickets" "Loading support requests"
                    "reloading_ticket" "Reloading support request"},
               "modules"
               #js {"support"
                    #js {"create_new_ticket" "Create new support request"
                         "submit" "Submit support request"
                         "reply_placeholder" "Write your reply here"
                         "no_tickets" "Currently no existing support requests"
                         "back_to_tickets" "Back to your support requests"
                         "deflect" "Before you submit a support request, please check to see if your question has already been answered on <a target=\"_blank\" href=\"https://discuss.circleci.com/\">Discuss</a>."}}})))
