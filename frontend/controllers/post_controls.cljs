(ns frontend.controllers.post-controls
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [goog.string :as gstring]
            goog.string.format
            [frontend.intercom :as intercom]
            [frontend.utils :as utils :refer [mlog]])
  (:require-macros [frontend.utils :refer [inspect]]))

(defmulti post-control-event!
  (fn [target message args previous-state current-state] message))

(defmethod post-control-event! :default
  [target message args previous-state current-state]
  (mlog "No post-control for: " message))

(defmethod post-control-event! :intercom-dialog-raised
  [target message dialog-message previous-state current-state]
  (intercom/raise-dialog (get-in current-state [:comms :errors]) dialog-message))

(defmethod post-control-event! :intercom-user-inspected
  [target message criteria previous-state current-state]
  (if-let [url (intercom/user-link)]
    (js/window.open url)
    (print "No matching url could be found from current window.location.pathname")))

(defmethod post-control-event! :show-all-branches-toggled
  [target message project-id previous-state current-state]
  ;;; XXX This should happen on routing, obviously
  ;; (print project-id
  ;;        " show-all-branches-toggled "
  ;;        (get-in previous-state [:settings :projects project-id :show-all-branches])
  ;;        " => "
  ;;        (get-in current-state [:settings :projects project-id :show-all-branches]))
  )

(defmethod post-control-event! :state-persisted
  [target message channel-id previous-state current-state]
  (.setItem js/localStorage "circle-state"
            (pr-str (dissoc current-state :comms))))

(defmethod post-control-event! :usage-queue-why-toggled
  [target message build-id previous-state current-state]
  ;; XXX these should probably be component-local
  (when (get-in current-state [:settings :builds build-id :show-usage-queue])
    (mlog "make this work")))

(defmethod post-control-event! :retry-build-clicked
  [target message {:keys [username reponame build_num build-id] :as args} previous-state current-state]
  (aset js/window "test" build-id)
  (let [api-ch (-> current-state :comms :api)]
    (put! api-ch [:retry-build :started args])
    (utils/ajax :post
                (gstring/format "/api/v1/project/%s/%s/%s/retry" username reponame build_num)
                :retry-build
                api-ch)))

(defmethod post-control-event! :selected-add-projects-org
  [target message args previous-state current-state]
  (let [login (:login args)
        type (:type args)
        api-ch (get-in current-state [:comms :api])]
    (utils/ajax :get
              (gstring/format "/api/v1/user/%s/%s/repos" (name type) login)
              :repos api-ch
              :context args)))
