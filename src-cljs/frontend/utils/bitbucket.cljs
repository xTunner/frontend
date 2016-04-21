(ns frontend.utils.bitbucket
  (:require
   [frontend.utils :as utils]
   [frontend.utils.vcs-url :as vcs-url]
   [cemerick.url :refer [url]]))

(defn http-endpoint []
  "https://bitbucket.org")

(defmethod vcs-url/profile-url :bitbucket [user]
  (str (http-endpoint) "/" (:login user)))

(defn auth-url []
  (let [auth-host (aget js/window "renderContext" "auth_host")
        state (merge {"return-to" (str js/window.location.pathname
                                       js/window.location.hash)
                      "token" (utils/oauth-csrf-token)}
                     (when (not= auth-host js/window.location.host)
                       {"delegate" (str js/window.location.protocol "//" js/window.location.host)}))
        state-str (.stringify js/JSON (clj->js state))]
    (-> (url (http-endpoint) "site/oauth2/authorize")
        (assoc :query {"state"        state-str
                       "response_type" "code"
                       "client_id" (aget js/window "renderContext" "bitbucketClientId")})
        str)))
