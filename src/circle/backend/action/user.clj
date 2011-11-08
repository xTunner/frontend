(ns circle.backend.action.user
  (:use [circle.backend.action.bash :only (remote-bash quasiquote)]))

(defn home-dir
  "returns the home dir on the current node"
  [build]
  (-> (remote-bash build (quasiquote (echo "$HOME")))
      :out
      (clojure.string/trim)))