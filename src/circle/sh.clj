(ns circle.sh
  "fns for running code locally, and compiling stevedore to bash"
  (:import java.io.OutputStreamWriter)
  (:require pallet.stevedore)
  (:require [clojure.java.shell :as sh])
  (:use [circle.util.coerce :only (to-name)])
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

(defn process
  "Like sh, but returns the process object, which can be used to safely stop the thread. Cmd is a bash strings. environment is a map of strings to strings."
  [cmd & {:keys [in environment pwd]}]
  (let [proc (.exec (Runtime/getRuntime)
                    "bash")]
    (with-open [osw (OutputStreamWriter. (.getOutputStream proc) "UTF-8")]
      (.write osw ^String (emit-form cmd :environment environment :pwd pwd)))
    proc))

(defn process-exit-code
  "Returns the process's exit code, or nil if it isn't finished yet"
  [p]
  (try
    (.exitValue p)
    (catch java.lang.IllegalThreadStateException e
      nil)))

(defn process-kill [p]
  (.destroy p))
