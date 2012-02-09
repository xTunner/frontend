(ns circle.backend.build.inference.clojure
  (:require [circle.sh :as sh])
  (:require fs)
  (:require [circle.backend.build.inference :as inference])
  (:use [circle.backend.action.bash :only (bash)])
  (:use circle.util.fs))

(defn dependencies
  "Extract the dependencies and dev dependencies from a project-clj"
  [project-clj]
  (letfn [(extract-assoc
            ; Get the value immediately following the given key if the key is
            ; not present, the empty vector is returned.
            [key-name last-val remaining]
            (if remaining
              (if (= key-name last-val)
                (first remaining)
                (recur key-name (first remaining) (next remaining)))
              []))]
    (into
      (extract-assoc :dependencies nil project-clj)
      (extract-assoc :dev-dependencies nil project-clj))))

(defn project-clj-file
  "Return the project.clj from the root of the repo or false if none is
  present."
  [repo]
  (when (fs/exists? (fs/join repo "project.clj"))
    (-> (fs/join repo "project.clj") (slurp) (read-string))))

(defn midje-is-a-dependency?
  "Determine if midje is listed as a dependency or dev-dependency of the
  project."
  [project-clj]
  (let [deps (dependencies project-clj)]
    (some #{'midje} (map first deps))))

(defn lein-deps []
  (bash (sh/q (lein deps))
        :type :setup
        :name "lein deps"))

(defn lein-midje [])
(defn lein-test [])

;; "Infered Clojure actions.
(defmethod inference/infer-actions* :clojure
  [_ repo]
  ;; If there's no project.clj, we can't infer shit.
  (when-let [project-clj (project-clj-file repo)]
    (filter identity
            [(lein-deps)
             (if (midje-is-a-dependency? project-clj)
               (lein-midje)
               (lein-test))])))

