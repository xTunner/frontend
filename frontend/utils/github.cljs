(ns frontend.utils.github
  (:require [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]))

;; XXX convert CI.github
(defn auth-url [& {:keys [scope]
                   :or {scope ["user:email" "repo"]}}]
  ;; XXX check on advanced compilation
  (js/CI.github.authUrl (clj->js scope)))

(defn gravatar-url [{:keys [gravatar_id login size]
                     :or {size 200}}]
  (cond (not (string/blank? gravatar_id))
        (gstring/format "https://secure.gravatar.com/avatar/%s?s=%s" gravatar_id size)

        (not (string/blank? login))
        (gstring/format "https://identicons.github.com/%s.png" login)

        :else "https://secure.gravatar.com/avatar/00000000000000000000000000000000?s=#{size}"))
