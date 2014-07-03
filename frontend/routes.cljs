(ns frontend.routes
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [put!]]
            [goog.events :as events]
            [frontend.models.project :as proj-mod]
            [frontend.utils :as utils :include-macros true]
            [secretary.core :as sec :include-macros true :refer [defroute]])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))


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

(defn v1-dashboard-path
  "Temporary helper method for v1-*-dashboard until we figure out how to
   make secretary's render-route work for multiple pages"
  [{:keys [org repo branch page]}]
  (let [url (cond branch (str "/gh/" org "/" repo "/tree/" branch)
                  repo (str "/gh/" org "/" repo)
                  org (str "/gh/" org)
                  :else "/")]
    (str url (when page (str "?page=" page)))))

(defn define-admin-routes! [nav-ch]
  (defroute v1-admin-recent-builds "/admin/recent-builds" []
    (open-to-inner! nav-ch :dashboard {:admin true})))


;; TODO: make this handle params
;; Creates a route that will ignore fragments and add them to params as {:_fragment "#fragment"}
(defrecord FragmentRoute [route]
  sec/IRenderRoute
  (render-route [this]
    route))

(extend-protocol sec/IRouteMatches
  FragmentRoute
  (route-matches [this route]
    (let [[normal-route fragment] (str/split route #"#" 2)]
      (when-let [match (sec/route-matches (sec/compile-route (:route this)) normal-route)]
        (merge match
               (when fragment {:_fragment fragment}))))))


(defn define-user-routes! [nav-ch authenticated?]
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
  (defroute v1-account "/account" []
    (open-to-inner! nav-ch :account {:subpage nil}))
  (defroute v1-account "/account/:subpage" [subpage]
    (open-to-inner! nav-ch :account {:subpage (keyword subpage)}))
  (defroute v1-logout "/logout" []
    (logout! nav-ch))

  (defroute v1-doc-root "/docs" []
    (open-to-outer! nav-ch :documentation-root {}))
  (defroute v1-doc-page #"/docs/(.*)" [doc-page]
    (open-to-outer! nav-ch :documentation-page {:page doc-page}))

  (defroute v1-about (FragmentRoute. "/about") {:as params}
    (open-to-outer! nav-ch :about params))

  (defroute v1-pricing (FragmentRoute. "/pricing") {:as params}
    (if authenticated?
      (open-to-inner! nav-ch :account {:subpage "plans"})
      (open-to-outer! nav-ch :pricing params)))

  (defroute v1-jobs (FragmentRoute. "/jobs") {:as params}
    (open-to-outer! nav-ch :jobs params))

  (defroute v1-privacy (FragmentRoute. "/privacy") {:as params}
    (open-to-outer! nav-ch :privacy params))

  (defroute v1-security (FragmentRoute. "/security") {:as params}
    (open-to-outer! nav-ch :security params))

  (defroute v1-security-hall-of-fame (FragmentRoute. "/security/hall-of-fame") {:as params}
    (open-to-outer! nav-ch :security-hall-of-fame params))

  (defroute v1-enterprise (FragmentRoute. "/enterprise") {:as params}
    (open-to-outer! nav-ch :enterprise params))


  ;; TODO: this should be stories/:company, but we'll wait for more stories
  (defroute v1-enterprise (FragmentRoute. "/stories/shopify") {:as params}
    (open-to-outer! nav-ch :shopify-story params))

  ;; TODO: this should be integrations/:integration, but we'll wait for more integrations
  (defroute v1-enterprise (FragmentRoute. "/integrations/docker") {:as params}
    (open-to-outer! nav-ch :docker-integration params))

  (defroute v1-enterprise (FragmentRoute. "/changelog") {:as params}
    (open-to-outer! nav-ch :changelog params))

  (defroute v1-root (FragmentRoute. "/") {:as params}
    (if authenticated?
      (open-to-inner! nav-ch :dashboard params)
      (open-to-outer! nav-ch :landing params))))

(defn define-spec-routes! [nav-ch]
  (defroute v1-not-found "*" []
    (open-to-outer! nav-ch :error {:status 404})))

(defn define-routes! [state]
  (let [nav-ch (get-in @state [:comms :nav])
        authenticated? (boolean (get-in @state [:current-user]))]
    (define-user-routes! nav-ch authenticated?)
    (when (get-in @state [:current-user :admin])
      (define-admin-routes! nav-ch))
    (define-spec-routes! nav-ch)))
