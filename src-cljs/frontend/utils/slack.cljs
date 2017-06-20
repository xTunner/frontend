(ns frontend.utils.slack
  (:require
   [frontend.utils :as utils]
   [cemerick.url :refer [url]]))

(defn auth-url []
  (let [auth-host (aget js/window "renderContext" "auth_host")
        state (merge {"return-to" (str js/window.location.pathname
                                       js/window.location.hash)
                      "token" (utils/oauth-csrf-token)}
                     (when (not= auth-host js/window.location.host)
                       {"delegate" (str js/window.location.protocol "//" js/window.location.host)}))
        state-str (.stringify js/JSON (clj->js state))]

    (-> (url "https://slack.com/oauth/authorize")
        (assoc :query {"state" state-str
                       "scope" "identity.basic,identity.email,identity.team,identity.avatar"
                       "response_type" "code"
                       "client_id" (aget js/window "renderContext" "slackClientId")})
        str)))
