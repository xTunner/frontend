(ns circle.backend.ssh
  (:require [clj-ssh.ssh :as ssh])
  (:use [clojure.tools.logging :only (errorf)])
  (:use [robert.bruce :only (try-try-again)])
  (:use [circle.util.args :only (require-args)])
  (:use [circle.util.core :only (apply-map)]))

(defn slurp-stream
  "given an input stream, read as much as possible and return it"
  [stream]
  (when (pos? (.available stream))
    (let [buffer-size clj-ssh.ssh/*piped-stream-buffer-size*
          bytes (byte-array buffer-size)
          num-read (.read stream bytes 0 buffer-size)
          s (String. bytes 0 num-read "UTF-8")]
      s)))

(defn ^:dynamic handle-out
  "Called periodically when the SSH command has output. Rebindable."
  [^String out-str]
  (print out-str))

(defn ^:dynamic handle-err [^String err-str]
  (print err-str))

(defn process-exec
  "Takes the exec map and processes it"
  [[shell stdout-stream stderr-stream]]
  (let [stdout (StringBuilder.)
        stderr (StringBuilder.)
        slurp-streams (fn slurp-streams []
                        (when-let [s (slurp-stream stdout-stream)]
                          (.append stdout s)
                          (handle-out s))
                        (when-let [s (slurp-stream stderr-stream)]
                          (.append stderr s)
                          (handle-err s)))]
    (while (= -1 (-> shell (.getExitStatus)))
      (slurp-streams)
      (Thread/sleep 100))
    (slurp-streams)
    {:exit (-> shell .getExitStatus)
     :out (str stdout)
     :err (str stderr)}))

(defn session
  "Creates an SSH session on an arbitrary box. All keys are
  required."
  [{:keys [username ip-addr public-key private-key]}]
  (require-args username ip-addr private-key)
  (let [agent (ssh/create-ssh-agent false)
        _ (ssh/add-identity agent "bogus"
                            (.getBytes private-key)
                            (when public-key
                              (.getBytes public-key)) nil)
        session (ssh/session agent
                             ip-addr
                             :username username
                             :strict-host-key-checking :no)]
    session))

(defn with-session
  "Calls f, a function of one argument, the ssh session, while connected."
  [session-args f]
  (let [s (session session-args)]
    (try-try-again
     {:sleep 1000
      :tries 30
      :catch [com.jcraft.jsch.JSchException]
      :error-hook (fn [e] (errorf "caught %s" e))}
     #(try
        (ssh/with-connection s
          (f s))))))


(defn remote-exec
  "Node is a map containing the keys required by with-session"
  [node ^String cmd]
  (with-session node
    (fn [ssh-session]
      (process-exec
       (ssh/ssh-exec ssh-session
                     cmd
                     nil
                     :stream
                     {})))))