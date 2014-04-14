(ns frontend.routes
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [goog.events :as events]
            [goog.history.Html5History :as html5-history]
            [frontend.models.project :as proj-mod]
            [frontend.utils :as utils]
            [secretary.core :as sec :include-macros true :refer [defroute]])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]])
  (:import [goog.history Html5History]
           [goog History]))

(defn listen-once-for-app!
  [app pred on-loaded]
  (let [listener-id   (keyword (utils/uuid))
        sentinel      (fn [_ _ _ new-state]
                        (when (pred new-state)
                          (remove-watch app listener-id)
                          (on-loaded new-state)))]
    (if (pred @app)
      (on-loaded @app)
      (add-watch app listener-id sentinel))))

(defn open-build-inspector!
  [app nav-ch org-id repo-id build-num]
  (let [id (str org-id "/" repo-id)]
    (put! nav-ch [:build-inspector [id build-num]])))

(defn open-to-root! [nav-ch]
  (put! nav-ch [:root]))

(defn open-to-add-projects! [nav-ch]
  (put! nav-ch [:add-projects]))

(defn define-routes! [app history-imp]
  (let [nav-ch (get-in @app [:comms :nav])]
    (defroute v1-inspect-build "/gh/:org-id/:repo-id/:build-num"
      [org-id repo-id build-num]
      (open-build-inspector! app nav-ch org-id repo-id build-num))
    (defroute v1-add-projects "/add-projects" []
      (open-to-add-projects! nav-ch))
    (defroute v1-root "/"
      [org-id repo-id build-num]
      (open-to-root! nav-ch)))
  ;; This triggers the dispatch on the above routes, when a deep link URL is provided.
  ;; goog.History(opt_invisible, opt_blankPageUrl, opt_input, opt_iframe)
  ;; (let [history-el (goog.history.Html5History. false nil history-el)]
  ;;   (goog.events/listen history-el "navigate" #(sec/dispatch! (.-token %)))
  ;;   (doto history-el
  ;;     (.setEnabled true)))
  (.setEnabled history-imp true)
  (goog.events/listen history-imp goog.history.EventType.NAVIGATE
                      #(do (print "token: " (aget % "token"))
                           (sec/dispatch! (str "/" (aget % "token"))))))
