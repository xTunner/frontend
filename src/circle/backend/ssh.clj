(ns circle.backend.ssh
  (:require [clj-ssh.ssh :as ssh])
  (:use [clojure.tools.logging :only (errorf)])
  (:require [clj-time.core :as time])
  (:use [robert.bruce :only (try-try-again)])
  (:use [circle.util.args :only (require-args)])
  (:use [circle.util.except :only (throwf)])
  (:use [arohner.utils :only (inspect)])
  (:use [slingshot.slingshot :only (throw+)])
  (:use [circle.util.core :only (apply-map)]))

(defn non-blocking-slurp
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
  [^String out-str])

(defn ^:dynamic handle-error [^String err-str])

(defn process-exec
  "Takes the exec map and processes it

Options:
   :relative-timeout - a joda period. terminate if it has been longer than period since the last stdout/stderr
   :absolute-timeout - a joda period. terminate if the command takes longer than period, regardless of output"
  [[shell stdout-stream stderr-stream] options]
  (let [stdout (StringBuilder.)
        stderr (StringBuilder.)
        start-time (time/now)
        relative (-> options :relative-timeout)
        absolute (-> options :absolute-timeout)
        end-time (when absolute
                   (time/plus start-time absolute))
        last-output (atom nil)
        reset-timeout (fn [] (swap! last-output (constantly (time/now))))
        slurp-streams (fn slurp-streams [& {:keys [final?]}]
                        (let [slurp-fn (if final?
                                         slurp
                                         non-blocking-slurp)]
                          (when-let [s (slurp-fn stdout-stream)]
                            (.append stdout s)
                            (reset-timeout)
                            (handle-out s))
                          (when-let [s (slurp-fn stderr-stream)]
                            (.append stderr s)
                            (reset-timeout)
                            (handle-error s))))]
    (while (= -1 (-> shell (.getExitStatus)))
      (when (and end-time (time/after? (time/now) end-time))
        (throw+ {:type ::ssh-timeout
                 :timeout-type :absolute
                 :timeout absolute}))
      (when (and relative (time/after? (time/now) (time/plus (or @last-output start-time) relative)))
        (throw+ {:type ::ssh-timeout
                 :timeout-type :relative
                 :timeout relative}))

      (slurp-streams)
      (Thread/sleep 100))
    (slurp-streams :final? true)
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

(defn retry-connect
  "Connect to the server, retrying on failure. Returns an ssh session"
  [session-args]
  (try-try-again
   {:sleep 1000
    :tries 30
    :catch [com.jcraft.jsch.JSchException]
    :error-hook (fn [e] (errorf "caught %s" e))}
   #(let [s (session session-args)]
      (ssh/connect s)
      s)))

(defn with-session
  "Calls f, a function of one argument, the ssh session, while connected."
  [session-args f]
  (let [session (retry-connect session-args)]
    (ssh/with-connection session
      (f session))))

(defn remote-exec-session [session cmd opts]
  (process-exec
   (ssh/ssh-exec session
                 cmd
                 nil
                 :stream
                 {})
   opts))

(defn remote-exec
  "Node is a map containing the keys required by with-session"
  [node ^String cmd & {:keys [timeout] :as opts}]
  (with-session node
    #(remote-exec-session % cmd (or opts {}))))

(defn scp
  "Scp one or more files. Direction is a keyword, either :to-remote
  or :to-local.

  The 'source' side of the connection may be a seq of
  strings. i.e. when transferring to-remote, local-path can be a
  seq. When transferring to-local, remote-path can be a seq of paths"

  [node & {:keys [local-path remote-path direction]}]
  (with-session node
    (fn [ssh-session]
      (cond
       (= :to-remote direction) (ssh/scp-to ssh-session local-path remote-path)
       (= :to-local direction) (ssh/scp-from ssh-session remote-path local-path)
       :else (throwf "direction must be :to-local or :to-remote")))))

(defn generate-keys
  "Generate a new pair of SSH keys, returns a map"
  []
  (let [agent (ssh/create-ssh-agent false)
        [priv pub] (clj-ssh.ssh/generate-keypair agent :rsa 1024 nil)]
    {:private-key (String. priv)
     :public-key (String. pub)}))