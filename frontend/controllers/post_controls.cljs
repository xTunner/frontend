(ns frontend.controllers.post-controls
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [dommy.core :as dommy]
            [frontend.controllers.api :as api]
            goog.dom
            goog.dom.classes
            [goog.string :as gstring]
            goog.string.format
            goog.style
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :refer [mlog]])
  (:require-macros [frontend.utils :refer [inspect]]
                   [dommy.macros :refer [node sel sel1]]))

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
  (when-not (= (get-in previous-state [:current-build :current-container-id])
               container-id)
    (let [parent (sel1 target "#container_parent")
          container (sel1 target (str "#container_" container-id))
          new-scroll-left (int (.-x (goog.style.getContainerOffsetToScrollInto container parent)))]
      (aset parent "scrollLeft" new-scroll-left))))

(defn container-id [container]
  (int (last (re-find #"container_(\d+)" (.-id container)))))

;; XXX: clean this up
(defmethod post-control-event! :container-parent-scroll
  [target message _ previous-state current-state]
  (let [controls-ch (get-in current-state [:comms :controls])
        current-container-id (get-in current-state [:current-build :current-container-id] 0)
        parent (sel1 target "#container_parent")
        parent-scroll-left (.-scrollLeft parent)
        current-container (sel1 target (str "#container_" current-container-id))
        current-container-scroll-left (int (.-x (goog.style.getContainerOffsetToScrollInto current-container parent)))
        parent-scroll-left (.-scrollLeft parent)
        containers (sort-by (fn [c] (Math/abs (- parent-scroll-left (.-x (goog.style.getContainerOffsetToScrollInto c parent)))))
                            (sel parent ".container-view"))
        ;; if we're scrolling left, then we want the container whose rightmost portion is showing
        ;; if we're scrolling right, then we want the container whose leftmost portion is showing
        new-scrolled-container-id (if (= parent-scroll-left current-container-scroll-left)
                                    current-container-id
                                    (if (< parent-scroll-left current-container-scroll-left)
                                      (apply min (map container-id (take 2 containers)))
                                      (apply max (map container-id (take 2 containers)))))]
    ;; This is kind of dangerous, we could end up with an infinite loop. Might want to
    ;; do a swap here (or find a better way to structure this!)
    (when (not= current-container-id new-scrolled-container-id)
      (put! controls-ch [:container-selected new-scrolled-container-id]))))

(defmethod post-control-event! :action-log-output-toggled
  [target message {:keys [index step] :as args} previous-state current-state]
  (when (and (get-in current-state [:current-build :steps step :actions index :show-output])
             (not (get-in current-state [:current-build :steps step :actions index :output])))
    (let [api-ch (get-in current-state [:comms :api])
          action (get-in current-state [:current-build :steps step :actions index])
          url (if (:output_url action)
                (:output_url action)
                (gstring/format "/api/v1/project/%s/%s/output/%s/%s"
                                (vcs-url/project-name (get-in current-state [:current-build :vcs_url]))
                                (get-in current-state [:current-build :build_num])
                                step
                                index))]
      (utils/ajax :get
                  url
                  :action-log
                  api-ch
                  :context args))))
