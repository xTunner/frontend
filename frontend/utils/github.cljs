(ns frontend.utils.github)

;; XXX convert CI.github
(defn auth-url [& {:keys [scope]
                   :or {scope ["user:email" "repo"]}}]
  ;; XXX check on advanced compilation
  (js/CI.github.authUrl (clj->js scope)))
