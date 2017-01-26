(ns frontend.analytics.amplitude
  (:require [goog.net.cookies :as cookies]
            [frontend.utils :as utils :include-macros]))

(def session-cookie-name "amplitude-session-id")

(defn session-id []
  (utils/swallow-errors
    (js/amplitude.getSessionId)))

(defn set-session-id-cookie!
  "Cookies the user with their amplitude session-id. Set the cookie
  path to be root so that it is available anywhere under the circleci domain.

  Set the opt_maxAge to -1 as it is a session cookie:
    https://google.github.io/closure-library/api/goog.net.Cookies.html"
  []
  (cookies/set session-cookie-name (session-id) -1 "/"))
