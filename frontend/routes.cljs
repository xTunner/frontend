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

(defn open-to-inner! [nav-ch navigation-point args]
  (put! nav-ch [navigation-point (assoc args :inner? true)]))

(defn open-to-outer! [nav-ch navigation-point args]
  (put! nav-ch [navigation-point (assoc args :inner? false)]))

(defn logout! [nav-ch]
  (put! nav-ch [:logout]))

(defn v1-build-path
  "Temporary helper method for v1-build until we figure out how to make
   secretary's render-route work for regexes"
  [org repo build-num]
  (str "/gh/" org "/" repo "/" build-num))

(defn define-routes! [app]
  (let [nav-ch (get-in @app [:comms :nav])]
    (defroute v1-org-dashboard "/gh/:org" {:as params}
      (open-to-inner! nav-ch :dashboard params))
    (defroute v1-project-dashboard "/gh/:org/:repo" {:as params}
      (open-to-inner! nav-ch :dashboard params))
    (defroute v1-project-branch-dashboard "/gh/:org/:repo/tree/:branch" {:as params}
      (open-to-inner! nav-ch :dashboard params))
    (defroute v1-build #"/gh/([^/]+)/([^/]+)/(\d+)"
      [org repo build-num]
      (open-to-inner! nav-ch :build {:project-name (str org "/" repo)
                                     :build-num (js/parseInt build-num)
                                     :org org
                                     :repo repo}))
    (defroute v1-project-settings "/gh/:org/:repo/edit"
      [org repo]
      (open-to-inner! nav-ch :project-settings {:project-name (str org "/" repo)
                                                :subpage nil
                                                :org org
                                                :repo repo}))
    (defroute v1-project-settings-subpage "/gh/:org/:repo/edit#:subpage"
      [org repo subpage]
      (open-to-inner! nav-ch :project-settings {:project-name (str org "/" repo)
                                                :subpage (keyword subpage)
                                                :org org
                                                :repo repo}))
    (defroute v1-org-settings "/gh/organizations/:org/settings"
      [org]
      (open-to-inner! nav-ch :org-settings {:org org :subpage nil}))
    (defroute v1-org-settings-subpage "/gh/organizations/:org/settings#:subpage"
      [org subpage]
      (open-to-inner! nav-ch :org-settings {:org org :subpage (keyword subpage)}))
    (defroute v1-add-projects "/add-projects" []
      (open-to-inner! nav-ch :add-projects {}))
    (defroute v1-logout "/logout" []
      (logout! nav-ch))
    (defroute v1-root "/" []
      (open-to-inner! nav-ch :dashboard {}))
    (defroute v1-not-found "*" []
      (open-to-outer! nav-ch :error {:status 404}))))
