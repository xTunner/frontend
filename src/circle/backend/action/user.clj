(ns circle.backend.action.user
  (:use [circle.backend.action.bash :only (remote-bash-build)])
  (:use [circle.sh :only (quasiquote)]))

(defn home-dir
  "returns the home dir on the current node"
  [build]
  (-> (remote-bash-build build (quasiquote (echo "$HOME")))
      :out
      (clojure.string/trim)))