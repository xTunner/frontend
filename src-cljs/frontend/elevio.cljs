(ns frontend.elevio
  (:require [frontend.utils :as utils]
            [frontend.models.user :as user]
            [frontend.utils :as utils]
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

(defn set-elev! [k v]
  (aset (aget js/window "_elev") k v))

(defn add-user-props!
  "Given a map of properties, add it to the list of user properties."
  [new-props]
  (let [current-props (-> js/window
                          (aget "_elev")
                          (aget "user")
                          js->clj)]
    (aset (aget js/window "_elev")
          "user"
          (-> (merge current-props new-props)
              utils/clj-keys-with-dashes->js-keys-with-underscores
              clj->js))))

(defn enable! [initial-user-data]
  (class-list/add js/document.body "circle-elevio")
  (aset js/window "_elev" (or (aget js/window "_elev") #js {}))
  ;; Some subset of the user data is currently set server side by adding
  ;; a js/window.elevSettings.user in a script tag in the DOM. We should
  ;; probably add all user traits client side, but as this is not broke I
  ;; am not going to fix it.
  (let [support-module-id "support"
        discuss-link-module-id 3003
        discuss-support-link-module-id 3762]
    (if user/support-eligible?
      ;; enable zendesk support, disable discuss support
      (set-elev! "disabledModules" #js [discuss-support-link-module-id])
      ;; enable discuss support, disable zendesk support
      (set-elev! "disabledModules" #js [support-module-id discuss-link-module-id]))
    (add-user-props! (-> js/window
                         (aget "elevSettings")
                         (aget "user")
                         js->clj
                         (merge initial-user-data)))
    (set-elev! "account_id" account-id)
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
                         "thankyou" "Thanks for submitting a support request. We try to respond to all tickets within 1-2 business days. If you need assistance sooner, <a target=\"_blank\" href=\"https://discuss.circleci.com/\">our community</a> may be able to help."
                         "delayed_appearance" ""
                         "deflect" "Before you submit a support request, please check to see if your question has already been answered on <a target=\"_blank\" href=\"https://discuss.circleci.com/\">Discuss</a>."}}})))
