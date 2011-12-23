(ns circle.backend.action.user
  (:use [circle.backend.action.bash :only (remote-bash-build)])
  (:use [circle.sh :only (q)]))

(defn home-dir
  "returns the home dir on the current node"
  [build]
  (-> (remote-bash-build build (q (echo "$HOME")))
      :out
      (clojure.string/trim)))