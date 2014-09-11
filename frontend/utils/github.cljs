(ns frontend.utils.github
  (:require [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [cemerick.url :refer [url]]))

;; TODO convert CI.github
(defn auth-url [& {:keys [scope]
                   :or {scope ["user:email" "repo"]}}]
  (js/CI.github.authUrl (clj->js scope)))

(defn make-avatar-url [avatar_url & {:keys [size] :or {size 200}}]
  (-> avatar_url
      url
      (assoc-in [:query "s"] size)
      str))
