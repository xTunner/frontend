(ns circle.util.chdir
  (:require fs)
  (:import (jnr.posix POSIXFactory
                      POSIXHandler))
  (:use midje.sweet))


(def handler (proxy [POSIXHandler]
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

(defn chdir [new]
  (let [abs (fs/normpath new)]
    (System/setProperty "user.dir" abs)
    (= (.chdir C abs) 0))) ; sets the dir for things that don't use "user.dir", like core/slurp