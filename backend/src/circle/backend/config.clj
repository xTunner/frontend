;; (ns circle.backend.config
;;   (:require [clj-yaml.core :as yaml])
;;   (:use [arohner.utils :only (fold)]))

;; ;; fns for loading config files

;; (defn read-file [path]
;;   (clj-yaml.core/parse-string (slurp path)))

;; (defn parse-action)

;; (defn key->map?
;;   "returns true if this form is a keyword mapping to map"
;;   [form]
;;   (and (= 2 (count form)) (keyword? (first form)) (map? (second form))))

;; (defn parse-actions [actions]
;;   (->>
;;    (for [action actions]
;;      (if (key->map? action)
;;        [(first action) (parse-action (second action))]
;;        (println "unrecognized action:" action)))
;;    (into {})))

;; (defn parse-project-form [project form]
;;   (let [form-name (first form)
;;         body (second form)]
;;     (condp = form-name
;;       :environment (update-in project [:environment] (parse-environment body))
;;       :actions (update-in project [:actions] (parse-actions body))
;;       (println "unrecognized form: " form-name))))

;; (defn parse-project [project]
;;   (fold [project {}] [form project]
;;     (if (key->map? form)
;;       (parse-project-form project form)
;;       (println "unrecognized form in project:" form))))

;; (defn parse-config [yaml]
;;   (fold [config {}] [form yaml]
;;     (if (key->map? form)
;;       (update-in config [:projects (first form)] (parse-project form))
;;       (println "unrecognized form at top level:" form))))

;; (defmulti parse-form dispatch-form)