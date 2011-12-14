(ns circle.sh
  "fns for running code locally, and compiling stevedore to bash"
  (:require pallet.stevedore)
  (:require [clojure.java.shell :as sh])
  (:use [circle.util.coerce :only (to-name)])
  (:use [circle.backend.build :only (*env*)])
  (:use [circle.util.core :only (apply-if)]))

(defmacro quasiquote [& forms]
  `(pallet.stevedore/quasiquote ~forms))

(defmacro q
  "shorthand for quasiquote"
  [& forms]
  `(quasiquote ~@forms))

(defn format-bash-cmd [body environment pwd]
  (let [cd-form (when (seq pwd)
                  (quasiquote (cd ~pwd)))
        env-form (map (fn [[k v]]
                        (format "export %s=%s" (to-name k) (to-name v))) environment)]
    (concat cd-form env-form body)))

(defn emit-form
  "Takes quoted stevedore code or a bash literal. Emits bash code, adding pwd
  and environment variables if necessary"
  [body & {:keys [environment
                  pwd]}]
  (let [body (if (string? body)
               [body]
               body)
        body (format-bash-cmd body environment pwd)]
    (pallet.stevedore/with-script-language :pallet.stevedore.bash/bash
      (pallet.stevedore/with-line-number [*file* (:line (meta body))]
        (binding [pallet.stevedore/*script-ns* *ns*]
          (pallet.stevedore/emit-script body))))))

(defn sh
  "Runs code in a subprocess. Body can be a string or stevedore code. Returns a map."
  [body & {:keys [environment pwd]}]
  ;; don't use c.j.shell's built-in support for :env and :dir, because
  ;; we'll have to use this code remotely, so prefer consistency
  ;; between local and remote.
  (sh/sh "bash" :in (emit-form body :environment environment :pwd pwd) 
         :return-map true))


(defmacro shq
  "like sh, but quasiquotes its arguments"
  [body & {:keys [environment pwd]}]
  `(sh (quasiquote ~body) :environment ~environment :pwd ~pwd))
