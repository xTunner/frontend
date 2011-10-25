(ns circle.backend.ssh
  (:require pallet.execute
            pallet.compute)
  (:use [circle.utils.args :only (require-args)])
  (:require [clj-ssh.ssh :as ssh]))

(defn slurp-stream
  "given an input stream, read as much as possible and return it"
  [stream]
  (when (pos? (.available stream))
    (let [buffer-size clj-ssh.ssh/*piped-stream-buffer-size*
          bytes (byte-array buffer-size)
          num-read (.read stream bytes 0 buffer-size)
          s (String. bytes 0 num-read "UTF-8")]
      s)))

(defn process-exec
  "Takes the exec map and processes it"
  [[shell stdout-stream stderr-stream]]
  (let [stdout (StringBuilder.)
        stderr (StringBuilder.)
        slurp-streams (fn slurp-streams []
                        (when-let [s (slurp-stream stdout-stream)]
                          (.append stdout s)
                          (print s))
                        (when-let [s (slurp-stream stderr-stream)]
                          (.append stderr s)
                          (print s)))]
    (while (= -1 (-> shell (.getExitStatus)))
      (slurp-streams)
      (Thread/sleep 100))
    (slurp-streams)
    {:exit (-> shell .getExitStatus)
     :out (str stdout)
     :err (str stderr)}))

(defn with-session
  "Creates an SSH session on an arbitrary box. All keys are
  required. f is a function of one argument, the ssh session, which
  can be used with functions in clj-ssh.ssh"
  [{:keys [username ip-addr public-key private-key]} f]
  (require-args username ip-addr public-key private-key)
  (ssh/with-ssh-agent []
    ;; JSch wants a name for each keypair, it will not store duplicate
    ;; keypair names on repeated calls to add-identity. But we always use a new agent, so we're fine.
    (let [_ (ssh/add-identity ssh/*ssh-agent* "bogus" 
                              (.getBytes private-key)
                              (.getBytes public-key) nil)
          session (ssh/session ip-addr
                               :username username
                               :strict-host-key-checking :no)]
      (ssh/with-connection session
        (f session)))))

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