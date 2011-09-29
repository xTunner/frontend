(ns circleci.backend.action.transfer
  (:require [clj-ssh.ssh :as ssh])
  (:require [circleci.backend.ssh :as circle-ssh])
  (:require pallet.compute))

(defn get-file
  "direction is either :get or :put"
  [context remote-path local-path]
  (circle-ssh/with-session (-> context :node) ssh-session
    (ssh/sftp ssh-session
              :get
              remote-path
              (-> local-path java.io.FileOutputStream.
                  java.io.BufferedOutputStream.))))