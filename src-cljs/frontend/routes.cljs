(ns frontend.routes
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [frontend.async :refer [put!]]
            [frontend.config :as config]
            [frontend.utils.vcs :as vcs]
            [secretary.core :as sec :refer-macros [defroute]])
  (:require-macros [frontend.utils :refer [inspect]]
                   [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn open-to-inner! [nav-ch navigation-point args]
  (put! nav-ch [navigation-point (assoc args :inner? true)]))

(defn open-to-outer! [nav-ch navigation-point args]
  (put! nav-ch [navigation-point (assoc args :inner? false)]))

(defn logout! [nav-ch]
  (put! nav-ch [:logout]))

(defn parse-build-page-fragment [fragment]
  (let [fragment-str (str fragment)
        [_ tab-name _ container-num]
        (re-find
          #"(tests|build-timing|artifacts|config|build-parameters|usage-queue|ssh-info)(/containers/(\d+))?"
          fragment-str)
        container-id (some-> container-num js/parseInt)
        tab (some-> tab-name keyword)]
    {:container-id (or container-id 0)
     :tab tab}))

(defn build-page-fragment [tab container-id]
  (cond
    (and tab container-id) (gstring/format "%s/containers/%s" (name tab) container-id)
    tab (name tab)
    :else nil))

(defn v1-build-path
  "Temporary helper method for v1-build until we figure out how to make
   secretary's render-route work for regexes"
  ([vcs_type org repo build-num]
   (v1-build-path vcs_type org repo build-num nil nil))
  ([vcs_type org repo build-num tab]
   (v1-build-path vcs_type org repo build-num tab nil))
  ([vcs_type org repo build-num tab container-id]
   (let [fragment (build-page-fragment tab container-id)]
     (str "/" (vcs/->short-vcs vcs_type) "/" org "/" repo "/" build-num (when fragment (str "#" fragment))))))

(defn v1-dashboard-path
  "Temporary helper method for v1-*-dashboard until we figure out how to
   make secretary's render-route work for multiple pages"
  [{:keys [vcs_type org repo branch page]}]
  (let [url (cond branch (str "/" (vcs/->short-vcs vcs_type) "/" org "/" repo "/tree/" branch)
                  repo (str "/" (vcs/->short-vcs vcs_type) "/" org "/" repo)
                  org (str "/" (vcs/->short-vcs vcs_type) "/" org)
                  :else "/dashboard")]
    (str url (when page (str "?page=" page)))))

(defn generate-url-str [format-str {:keys [vcs_type _fragment] :as params}]
  (let [short-vcs-type (if vcs_type
                         (vcs/->short-vcs vcs_type)
                         "gh")
        new-params (assoc params :vcs_type short-vcs-type)
        url (sec/render-route format-str new-params)
        new-fragment (when _fragment
                       (name _fragment))]
    (if new-fragment
      (str url "#" new-fragment)
      url)))

(defn v1-org-settings-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/organizations/:org/settings" params))

(defn v1-projects-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/projects" params))

(defn v1-organization-projects-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/projects/:vcs_type/:org" params))

(defn v1-project-dashboard-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/:org/:repo" params))

(defn v1-project-settings-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/:org/:repo/edit" params))

(defn v1-insights-project-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/build-insights/:vcs_type/:org/:repo/:branch" params))

(defn v1-add-projects-path
  [params]
  (generate-url-str "/add-projects" params))

(defn v1-admin-fleet-state-path
  [params]
  (generate-url-str "/admin/fleet-state" params))

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
  (defroute v1-admin-config "/admin/system-settings" []
    (open-to-inner! nav-ch :admin-settings {:admin true
                                            :subpage :system-settings}))
  (defroute v1-admin-system-management "/admin/management-console" []
    (.replace js/location
              ;; System management console is served at port 8800
              ;; with replicated and it's always https
              (str "https://" js/window.location.hostname ":8800/")))

  (defroute v1-admin-license "/admin/license" []
    (open-to-inner! nav-ch :admin-settings {:admin true
                                            :subpage :license})))


(defn define-user-routes! [nav-ch authenticated?]
  (defroute v1-org-settings #"/(gh|bb)/organizations/([^/]+)/settings"
    [short-vcs-type org _ maybe-fragment]
    (open-to-inner! nav-ch :org-settings {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                          :org org
                                          :subpage (keyword (:_fragment maybe-fragment))}))

  (defroute v1-org-dashboard-alternative #"/(gh|bb)/organizations/([^/]+)" [short-vcs-type org params]
    (open-to-inner! nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org})))

  (defroute v1-org-dashboard #"/(gh|bb)/([^/]+)" [short-vcs-type org params]
    (open-to-inner! nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org})))

  (defroute v1-project-dashboard #"/(gh|bb)/([^/]+)/([^/]+)" [short-vcs-type org repo params]
    (open-to-inner! nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org
                                              :repo repo})))

  (defroute v1-project-branch-dashboard #"/(gh|bb)/([^/]+)/([^/]+)/tree/(.+)" ; workaround secretary's annoying auto-decode
    [short-vcs-type org repo branch params]
    (open-to-inner! nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org
                                              :repo repo
                                              :branch branch})))

  (defroute v1-build #"/(gh|bb)/([^/]+)/([^/]+)/(\d+)"
    [short-vcs-type org repo build-num _ maybe-fragment]
    ;; normal destructuring for this broke the closure compiler
    (let [_fragment (:_fragment maybe-fragment)
          {:keys [tab container-id]} (parse-build-page-fragment _fragment)]
      (open-to-inner! nav-ch :build {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                     :project-name (str org "/" repo)
                                     :build-num (js/parseInt build-num)
                                     :org org
                                     :repo repo
                                     :tab tab
                                     :container-id container-id})))

  (defroute v1-project-settings #"/(gh|bb)/([^/]+)/([^/]+)/edit" [short-vcs-type org repo _ maybe-fragment]
    (open-to-inner! nav-ch :project-settings {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :project-name (str org "/" repo)
                                              :subpage (keyword (:_fragment maybe-fragment))
                                              :org org
                                              :repo repo}))

  (defroute v1-add-projects "/add-projects" {:keys [_fragment]}
    (open-to-inner! nav-ch :add-projects {:tab _fragment}))
  (defroute v1-insights "/build-insights" []
    (open-to-inner! nav-ch :build-insights {}))
  (defroute v1-insights-project #"/build-insights/(gh|bb)/([^/]+)/([^/]+)/([^/]+)" [short-vcs-type org repo branch]
    (open-to-inner! nav-ch :project-insights {:org org :repo repo :branch branch :vcs_type (vcs/->lengthen-vcs short-vcs-type)}))
  (defroute v1-account "/account" []
    (open-to-inner! nav-ch :account {:subpage :notifications}))
  (defroute v1-account-subpage "/account/:subpage" [subpage]
    (open-to-inner! nav-ch :account {:subpage (keyword subpage)}))
  (defroute v1-organization-projects "/projects/:vcs_type/:org" {:keys [vcs_type org]}
    (open-to-inner! nav-ch :projects {:vcs_type vcs_type
                                      :org org}))
  (defroute v1-projects "/projects" []
    (open-to-inner! nav-ch :projects {}))
  (defroute v1-team "/team" []
    (open-to-inner! nav-ch :team {}))
  (defroute v1-logout "/logout" []
    (logout! nav-ch))

  (defroute v1-mobile "/mobile" {:as params}
    (open-to-outer! nav-ch :mobile (assoc params
                                     :_title "Mobile Continuous Integration and Mobile App Testing"
                                     :_description "Build 5-star mobile apps with Mobile Continuous Integration by automating your build, test, and deployment workflow on iOS and Android. ")))

  (defroute v1-ios "/mobile/ios" {:as params}
    (open-to-outer! nav-ch :ios (assoc params
                                  :_title "Apple iOS App Testing"
                                  :_description "Build 5-star iOS apps by automating your development workflow with Mobile Continuous Integration and Delivery.")))

  (defroute v1-android "/mobile/android" {:as params}
    (open-to-outer! nav-ch :android (assoc params
                                      :_title "Android App Testing"
                                      :_description "Build better Android apps with Mobile Continuous Integration. Get testing today!")))

  (defroute v1-pricing "/pricing" {:as params}
    (open-to-outer! nav-ch :pricing (assoc params
                                        :_analytics-page "View Pricing Outer"
                                        :_title "Pricing and Information"
                                        :_description "Save time and cost by making your engineering team more efficient. Get started for free and see how many containers and parallelism you need to scale with your team.")))

  (defroute v1-jobs "/jobs" {:as params}
    (open-to-outer! nav-ch :jobs (assoc params
                                   :_analytics-page "View jobs"
                                   :_title "Search for Jobs at CircleCI"
                                   :_description "Come work with us. Join our amazing team of highly technical engineers and business leaders to help us build great developer tools.")))

  (defroute v1-privacy "/privacy" {:as params}
    (open-to-outer! nav-ch :privacy (assoc params
                                      :_analytics-page "View Privacy"
                                      :_title "Privacy Policy"
                                      :_description "Read our privacy policy to understand how we collect and use information about you.")))

  (defroute v1-security "/security" {:as params}
    (open-to-outer! nav-ch :security (assoc params
                                       :_analytics-page "View Security"
                                       :_title "Security Policy"
                                       :_description "Read our security policy and guidelines and see how your data is safe with CircleCI.")))

  (defroute v1-security-hall-of-fame "/security/hall-of-fame" {:as params}
    (open-to-outer! nav-ch :security-hall-of-fame (assoc params
                                                    :_title "Security Hall of Fame"
                                                    :_description "Join our Security Hall of Fame by helping us make our platform more secure."
                                                    :_analytics-page "View Security Hall of Fame")))

  (defroute v1-enterprise "/enterprise" {:as params}
    (open-to-outer! nav-ch :enterprise (assoc params
                                         :_title "Enterprise Continuous Integration and Deployment"
                                         :_description "Reduce risk with Enterprise Continuous Integration from CircleCI. Integrates seamlessly with Github Enterprise and the rest of your technology stack.")))

  (defroute v1-enterprise-aws "/enterprise/aws" {:as params}
    (open-to-outer! nav-ch :aws (assoc params
                                         :_title "CircleCI Enterprise on AWS"
                                         :_description "Run CircleCI Enterprise on the same AWS infrastructure you use for everything else. Integrates seamlessly with GitHub Enterprise on AWS.")))

  (defroute v1-enterprise-azure "/enterprise/azure" {:as params}
    (open-to-outer! nav-ch :azure (assoc params
                                         :_title "CircleCI Enterprise on Azure"
                                         :_description "Install CircleCI Enterprise in your own Microsoft Azure account. Integrates with GitHub Enterprise on Azure and Active Directory authentication.")))

  (defroute v1-stories "/stories/:story" [story]
    (open-to-outer! nav-ch :stories {:story (keyword story)}))

  (defroute v1-features "/features" {:as params}
    (open-to-outer! nav-ch :features (assoc params
                                       :_title "Continuous Integration Product and Features"
                                       :_description "Build a better product and let CircleCI handle your testing. CircleCI helps your team improve productivity with faster development, reduced risk, and better code.")))

  (defroute v1-languages "/features/:language" {:as params}
    (open-to-outer! nav-ch :language-landing params))

  (defroute v1-integrations "/integrations/:integration" [integration]
    (open-to-outer! nav-ch :integrations {:integration (keyword integration)}))

  (defroute v1-root "/" {:as params}
    (if authenticated?
      (open-to-inner! nav-ch :dashboard params)
      (open-to-outer! nav-ch :landing (assoc params :_canonical "/"))))

  (defroute v1-dashboard "/dashboard" {:as params}
    (open-to-inner! nav-ch :dashboard params))

  (defroute v1-home "/home" {:as params}
    (open-to-outer! nav-ch :landing (assoc params :_canonical "/")))

  (defroute v1-press "/press" {:as params}
    (open-to-outer! nav-ch :press (assoc params
                                         :_title "Press Releases and Updates"
                                         :_description "Find the latest CircleCI news and updates here.")))

  (defroute v1-signup "/signup" {:as params}
    (open-to-outer! nav-ch :signup params)))

(defn define-spec-routes! [nav-ch]
  (defroute trailing-slash #"(.+)/$" [path]
    (put! nav-ch [:navigate! {:path path :replace-token? true}]))
  (defroute v1-not-found "*" []
    (open-to-outer! nav-ch :error {:status 404})))

(defn define-routes! [state nav-ch]
  (let [authenticated? (boolean (get-in state [:current-user]))]
    (define-user-routes! nav-ch authenticated?)
    (when (get-in state [:current-user :admin])
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
