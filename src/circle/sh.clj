(ns circle.sh
  (:require pallet.stevedore)
  (:require [clojure.contrib.shell :as sh])
  (:use [circle.util.predicates :only (named?)])
  (:use [circle.backend.build :only (*pwd* *env*)])
  (:use [circle.util.core :only (apply-if)]))

(defmacro quasiquote [& forms]
  `(pallet.stevedore/quasiquote ~forms))

(defn maybe-name
  "Returns "
  [x]
  (apply-if (named? x) name x))

(defn format-bash-cmd [body environment pwd]
  (let [cd-form (when (seq pwd)
                  (quasiquote (cd ~pwd)))
        env-form (map (fn [[k v]]
                        (format "export %s=%s" (maybe-name k) (maybe-name v))) environment)]
    (concat cd-form env-form body)))

(defn emit-form
  "Takes quoted stevedore code or a bash literal. Emits bash code, adding pwd
  and environment variables if necessary"
  [body & {:keys [environment
                  pwd]}]
  (let [body (if (string? body)
               [body]
               body)
        body (format-bash-cmd body environment (or pwd *pwd*))]
    (pallet.stevedore/with-script-language :pallet.stevedore.bash/bash
      (pallet.stevedore/with-line-number [*file* (:line (meta body))]
        (binding [pallet.stevedore/*script-ns* *ns*]
          (pallet.stevedore/emit-script body))))))

(defn sh
  "Runs code in a subprocess. Body can be a string or stevedore code. Returns a map."
  [body & {:keys [environment pwd]}]
  (sh/sh "bash" :in (emit-form body :environment environment :pwd pwd) :return-map true))