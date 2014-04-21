(ns frontend.controllers.post-controls
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [goog.string :as gstring]
            goog.string.format
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
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
  [target message {:keys [username reponame
                          build_num build-id]} previous-state current-state]
  (when (get-in current-state [:current-build :show-usage-queue])
    (let [api-ch (get-in current-state [:comms :api])]
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/%s/%s/usage-queue"
                                  username reponame build_num)
                  :usage-queue
                  api-ch
                  :context build-id))))

(defmethod post-control-event! :show-artifacts-toggled
  [target message {:keys [username reponame
                          build_num build-id]} previous-state current-state]
  (when (get-in current-state [:current-build :show-artifacts])
    (let [api-ch (get-in current-state [:comms :api])]
      (utils/ajax :get
                  (gstring/format "/api/v1/project/%s/%s/%s/artifacts"
                                  username reponame build_num)
                  :build-artifacts
                  api-ch
                  :context build-id))))

(defmethod post-control-event! :retry-build-clicked
  [target message {:keys [username reponame build_num build-id] :as args} previous-state current-state]
  (let [api-ch (-> current-state :comms :api)]
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
              :repos
              api-ch
              :context args)))

(defmethod post-control-event! :followed-repo
  [target message repo previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (api/ajax :post
              (gstring/format "/api/v1/project/%s/follow" (vcs-url/project-name (:vcs_url repo)))
              :followed-repo
              api-ch
              :context repo)))

(defmethod post-control-event! :container-selected
  [target message container-id previous-state current-state]
  (assoc-in state [:current-build :current-container-id] container-id))
