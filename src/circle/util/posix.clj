(ns circle.util.posix
  (:require fs)
  (:import (org.jruby.ext.posix POSIXFactory
                                POSIXHandler))
  (:require [circle.sh :as sh])
  (:require [clojure.string :as str])
  (:use midje.sweet))

(def handler
  (proxy [POSIXHandler]
      []
    (error [error extra]
      (println "error:" error extra))
    (unimplementedError [methodname]
      (throw (Exception. (format "unimplemented method %s" methodname))))
    (warn [warn-id message & data]
      (println "warning:" warn-id message data))
    (isVerbose []
      false)
    (getCurrentWorkingDirectory []
      (System/getProperty "user.dir"))
    (getEnv []
      (map str (System/getenv)))
    (getInputStream []
      System/in)
    (getOutputStream []
      System/out)
    (getErrorStream []
      System/err)
    (getPID []
      (rand-int 65536))))

(def C (POSIXFactory/getPOSIX handler true))

(defn pid
  "Returns the pid of the current JVM"
  []
  (.getpid C))

(defn- parse-process-line
  "Given a line of output from ps, return a vector of [pid, parent-pid]"
  [l]
  (-> l
      str/trim
      (str/split #" ")
      (#(vector (Integer/parseInt (first %)) (Integer/parseInt (last %))))))

(defn children
  "returns the seq of pids of children of the given process"
  [parent-id]
  (-> (sh/shq "ps -o pid,ppid -ax")
      :out
      (str/split #"\n")
      (rest)
      (->>
       (map parse-process-line)
       (filter #(= parent-id (second %)))
       (map first))))

(defn kill
  "Sends a kill signal. signal is an int. From the man page:

     1       HUP (hang up)
     2       INT (interrupt)
     3       QUIT (quit)
     6       ABRT (abort)
     9       KILL (non-catchable, non-ignorable kill)
     14      ALRM (alarm clock)
     15      TERM (software termination signal)
"
  [pid signal]
  (.kill C pid signal))

(defn kill-all-children [& {:keys [signal]}]
  (let [pid (pid)
        signal (or signal 15)]
    (doseq [c (children pid)]
      (kill c signal))))