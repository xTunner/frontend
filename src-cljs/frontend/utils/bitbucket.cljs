(ns frontend.utils.bitbucket
  (:require
   [frontend.utils :as utils]
   [cemerick.url :refer [url]]))

(defn http-endpoint []
  "https://bitbucket.org"
  ;;(config/bitbucket-endpoint)
  )

(defn auth-url []
  (let [;; auth-host and auth-protocol indicate the circle host that
        ;; will handle the oauth redirect
        auth-host (aget js/window "renderContext" "auth_host")
        auth-protocol (aget js/window "renderContext" "auth_protocol")
        redirect (-> (str auth-protocol "://" auth-host)
                     (url  "auth/bitbucket")
                     (assoc :query (merge {"return-to" (str js/window.location.pathname
                                                            js/window.location.hash)}
                                          (when (not= auth-host js/window.location.host)
                                            ;; window.location.protocol includes the colon
                                            {"delegate" (str js/window.location.protocol "//" js/window.location.host)})))
                     (assoc :protocol (or (aget js/window "renderContext" "auth_protocol")
                                          "https"))
                     str)]
    (-> (url (http-endpoint) "site/oauth2/authorize")
        (assoc :query {"redirect_uri" redirect
                       "state" (utils/oauth-csrf-token)
                       "response_type" "code"
                       "client_id" (aget js/window "renderContext" "bitbucketClientId")})
        str)))
