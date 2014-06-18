(ns frontend.routes
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [goog.events :as events]
            [goog.history.Html5History :as html5-history]
            [frontend.models.project :as proj-mod]
            [frontend.utils :as utils :include-macros true]
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
  [app nav-ch org repo build-num]
  (let [project-name (str org "/" repo)]
    (put! nav-ch [:build [project-name build-num org repo]])))

(defn open-to-dashboard! [nav-ch & [args]]
  (put! nav-ch [:dashboard args]))

(defn open-to-add-projects! [nav-ch]
  (put! nav-ch [:add-projects]))

;; XXX validate subpage, send to 404
(defn open-to-project-settings! [nav-ch org repo subpage]
  (let [project-name (str org "/" repo)]
    (put! nav-ch [:project-settings {:project-name project-name
                                     :subpage subpage
                                     :org org
                                     :repo repo}])))

;; XXX validate subpage, send to 404
(defn open-to-org-settings!
  ([nav-ch org]
   (open-to-org-settings! nav-ch org :projects))
  ([nav-ch org subpage]
   (put! nav-ch [:org-settings {:subpage subpage
                                :org-name org}])))

(defn define-routes! [app]
  (let [nav-ch (get-in @app [:comms :nav])]
    (defroute v1-org-dashboard "/gh/:org" {:as params}
      (open-to-dashboard! nav-ch params))
    (defroute v1-project-dashboard "/gh/:org/:repo" {:as params}
      (open-to-dashboard! nav-ch params))
    (defroute v1-project-branch-dashboard "/gh/:org/:repo/tree/:branch" {:as params}
      (open-to-dashboard! nav-ch params))
    (defroute v1-inspect-build #"/gh/([^/]+)/([^/]+)/(\d+)"
      [org repo build-num]
      (open-build-inspector! app nav-ch org repo (js/parseInt build-num)))
    (defroute v1-project-settings "/gh/:org/:repo/edit"
      [org repo]
      (open-to-project-settings! nav-ch org repo nil))
    (defroute v1-project-settings-subpage "/gh/:org/:repo/edit#:subpage"
      [org repo subpage]
      (open-to-project-settings! nav-ch org repo (keyword subpage)))
    (defroute v1-org-settings "/gh/organizations/:org/settings"
      [org]
      (open-to-org-settings! nav-ch org))
    (defroute v1-org-settings-subpage "/gh/organizations/:org/settings#:subpage"
      [org subpage]
      (open-to-org-settings! nav-ch org (keyword subpage)))
    (defroute v1-add-projects "/add-projects" []
      (open-to-add-projects! nav-ch))
    (defroute v1-root "/" []
      (open-to-dashboard! nav-ch))))
