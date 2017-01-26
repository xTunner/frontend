(ns frontend.analytics.amplitude
  (:require [goog.net.cookies :as cookies]
            [frontend.utils :as utils :refer-macros [swallow-errors]]))

(def session-cookie-name "amplitude-session-id")

(defn session-id []
  (swallow-errors
    (or (js/amplitude.getSessionId) -1)))

(defn set-session-id-cookie!
  "Cookies the user with their amplitude session-id. Set the cookie
  path to be root so that it is available anywhere under the circleci domain.

  Set the opt_maxAge to -1 as it is a session cookie:
    https://google.github.io/closure-library/api/goog.net.Cookies.html"
  []
  (let [sid (session-id)]
    (println "setting cookie with sid" sid)
    (cookies/set session-cookie-name sid -1 "/")
    (println "set" (cookies/get session-cookie-name))
    (when (not (cookies/get session-cookie-name))
      (println js/amplitude)
      (println js/window.amplitude))))
