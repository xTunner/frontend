(ns frontend.routes
  (:require [clojure.string :as str]
            [frontend.async :refer [put!]]
            [frontend.config :as config]
            [secretary.core :as sec :refer-macros [defroute]])
  (:require-macros [frontend.utils :refer [inspect]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))


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
  (defroute v1-admin-switch "/admin/switch" []
    (open-to-inner! nav-ch :switch {:admin true}))
  (defroute v1-admin-recent-builds "/admin/recent-builds" []
    (open-to-inner! nav-ch :dashboard {:admin true}))
  (defroute v1-admin-current-builds "/admin/running-builds" []
    (open-to-inner! nav-ch :dashboard {:admin true
                                       :query-params {:status "running"}}))
  (defroute v1-admin-queued-builds "/admin/queued-builds" []
    (open-to-inner! nav-ch :dashboard {:admin true
                                       :query-params {:status "scheduled,queued"}}))
  (defroute v1-admin-deployments "/admin/deployments" []
    (open-to-inner! nav-ch :dashboard {:deployments true}))
  (defroute v1-admin-build-state "/admin/build-state" []
    (open-to-inner! nav-ch :build-state {:admin true}))

  (defroute v1-admin "/admin" []
    (open-to-inner! nav-ch :admin-settings {:admin true
                                            :subpage nil}))
  (defroute v1-admin-fleet-state "/admin/fleet-state" [_fragment]
    (open-to-inner! nav-ch :admin-settings {:admin true
                                            :subpage :fleet-state
                                            :tab (keyword _fragment)}))
  (defroute v1-admin-users "/admin/users" []
    (open-to-inner! nav-ch :admin-settings {:admin true
                                            :subpage :users}))
  (defroute v1-admin-system-management "/admin/management-console" []
    (.replace js/location
              ;; System management console is served at port 8800
              ;; with replicated and it's always https
              (str "https://" js/window.location.hostname ":8800/")))

  (defroute v1-admin-license "/admin/license" []
    (open-to-inner! nav-ch :admin-settings {:admin true
                                            :subpage :license})))


(defn define-user-routes! [nav-ch authenticated?]
  (defroute v1-org-settings "/gh/organizations/:org/settings"
    [org _fragment]
    (open-to-inner! nav-ch :org-settings {:org org :subpage (keyword _fragment)}))
  (defn v1-org-settings-subpage [params]
    (apply str (v1-org-settings params)
         (when-let [subpage (:subpage params)]
           ["#" subpage])))
  (defroute v1-org-dashboard-alternative "/gh/organizations/:org" {:as params}
    (open-to-inner! nav-ch :dashboard params))
  (defroute v1-org-dashboard "/gh/:org" {:as params}
    (open-to-inner! nav-ch :dashboard params))
  (defroute v1-project-dashboard "/gh/:org/:repo" {:as params}
    (open-to-inner! nav-ch :dashboard params))
  (defroute v1-project-branch-dashboard #"/gh/([^/]+)/([^/]+)/tree/(.+)" ; workaround secretary's annoying auto-decode
    [org repo branch args]
    (open-to-inner! nav-ch :dashboard (merge args {:org org :repo repo :branch branch})))
  (defroute v1-build #"/gh/([^/]+)/([^/]+)/(\d+)"
    [org repo build-num _ maybe-fragment]
    ;; normal destructuring for this broke the closure compiler
    (let [_fragment (:_fragment maybe-fragment)]
      (open-to-inner! nav-ch :build {:project-name (str org "/" repo)
                                    :build-num (js/parseInt build-num)
                                    :org org
                                    :repo repo
                                    :tab (keyword _fragment)})))
  (defroute v1-project-settings "/gh/:org/:repo/edit"
    [org repo _fragment]
    (open-to-inner! nav-ch :project-settings {:project-name (str org "/" repo)
                                              :subpage (keyword _fragment)
                                              :org org
                                              :repo repo}))
  (defn v1-project-settings-subpage [params]
    (apply str (v1-project-settings params)
           (when-let [subpage (:subpage params)]
             ["#" subpage])))
  (defroute v1-add-projects "/add-projects" []
    (open-to-inner! nav-ch :add-projects {}))
  (defroute v1-insights "/build-insights" []
    (open-to-inner! nav-ch :build-insights {}))
  (defroute v1-insights-dashboard "/build-insights/dashboard/:org/:repo" [org repo]
    (open-to-inner! nav-ch :build-insights {:org org :repo repo}))
  (defroute v1-invite-teammates "/invite-teammates" []
    (open-to-inner! nav-ch :invite-teammates {}))
  (defroute v1-invite-teammates-org "/invite-teammates/organization/:org" [org]
    (open-to-inner! nav-ch :invite-teammates {:org org}))
  (defroute v1-account "/account" []
    (open-to-inner! nav-ch :account {:subpage nil}))
  (defroute v1-account-subpage "/account/:subpage" [subpage]
    (open-to-inner! nav-ch :account {:subpage (keyword subpage)}))
  (defroute v1-logout "/logout" []
    (logout! nav-ch))

  (defroute v1-doc "/docs" []
    (if (config/enterprise?)
      (.replace js/location "https://circleci.com/docs")
      (open-to-outer! nav-ch :documentation {})))
  (defroute v1-doc-subpage "/docs/:subpage" {:keys [subpage] :as params}
    (if (config/enterprise?)
      (.replace js/location (str "https://circleci.com/docs/" subpage))
      (open-to-outer! nav-ch :documentation (assoc params :subpage (keyword subpage)))))

  (defroute v1-jobs "/jobs" {:as params}
    (open-to-outer! nav-ch :jobs (assoc params
                                   :_analytics-page "View jobs"
                                   :_title "Search for Jobs at CircleCI"
                                   :_description "Come work with us. Join our amazing team of highly technical engineers and business leaders to help us build great developer tools.")))

  (defroute v1-changelog-individual "/changelog/:id" {:as params}
    (open-to-outer! nav-ch :changelog params))

  (defroute v1-changelog "/changelog" {:as params}
    (open-to-outer! nav-ch :changelog params))

  (defroute v1-root "/" {:as params}
    (if authenticated?
      (open-to-inner! nav-ch :dashboard params)
      (open-to-outer! nav-ch :landing (assoc params :_canonical "/"))))

  (defroute v1-home "/home" {:as params}
    (open-to-outer! nav-ch :landing (assoc params :_canonical "/"))))

(defn define-spec-routes! [nav-ch]
  (defroute trailing-slash #"(.+)/$" [path]
    (put! nav-ch [:navigate! {:path path :replace-token? true}]))
  (defroute v1-not-found "*" []
    (open-to-outer! nav-ch :error {:status 404})))

(defn define-routes! [state]
  (let [nav-ch (get-in @state [:comms :nav])
        authenticated? (boolean (get-in @state [:current-user]))]
    (define-user-routes! nav-ch authenticated?)
    (when (get-in @state [:current-user :admin])
      (define-admin-routes! nav-ch))
    (define-spec-routes! nav-ch)))

(defn parse-uri [uri]
  (let [[uri-path fragment] (str/split (sec/uri-without-prefix uri) "#")
        [uri-path query-string] (str/split uri-path  #"\?")
        uri-path (sec/uri-with-leading-slash uri-path)]
    [uri-path query-string fragment]))

(defn dispatch!
  "Dispatch an action for a given route if it matches the URI path."
  ;; Based on secretary.core: https://github.com/gf3/secretary/blob/579bc224f23e6c26a2299a2e5a48491fd3792faf/src/secretary/core.cljs#L314
  [uri]
  (let [[uri-path query-string fragment] (parse-uri uri)
        query-params (when query-string
                       {:query-params (sec/decode-query-params query-string)})
        {:keys [action params]} (sec/locate-route uri-path)
        action (or action identity)
        params (merge params query-params {:_fragment fragment})]
    (action params)))
