(ns circleci.backend.rails
  (:require [clojure.java.io :as io])
  (:require [circleci.backend.nodes :as nodes])
  (:require [pallet.action :as action]
            [pallet.action.remote-file :as remote-file]
            [pallet.script.lib :as lib])
  (:require [clj-ssh.ssh :as ssh])
  (:use [clojure.pprint :only (pprint)]
        [pallet.thread-expr :only (for->)]))

;; code for building rails

(defn checkout []
  (pallet.core/lift nodes/builder-group
                    :compute (pallet.compute/service :aws)
                    :phase (pallet.phase/phase-fn (pallet.action.exec-script/exec-script
                                                   (git clone "git://github.com/rails/rails.git")
                                                   (cd "rails")
                                                   (sudo bundler install))))) ;; sudo, building gems as sudoer makes root own ~/.gems))))

(defn filenames
  "given a seq of LsEntries, returns a map of file names"
  [files]
  (->> files
       (map #(.getFilename %))
       (remove #(contains? #{"." ".."} %))))

(action/def-clj-action map-remote-files
  ;; transfers each file that matches the glob to the local box, applies f with a java.io.File for each file
  [session dir f]
  
  (let [ssh-session (-> session :ssh :ssh-session)
        _ (assert ssh-session)
        files (ssh/sftp ssh-session :ls dir)]
    (doseq [file (filenames files)]
      (let [local-file (pallet.utils/tmpfile)]
        (with-open [outstream (io/output-stream local-file)]
          (ssh/sftp ssh-session :get (str dir "/" file) outstream)
          (f local-file)
          (.delete local-file))))
    session))

(defn test []
  ;; startup node
  (pallet.core/lift nodes/builder-group
                    :compute (pallet.compute/service :aws)
                    :phase (pallet.phase/phase-fn (pallet.action.exec-script/exec-script
                                                   (cd "rails")
                                                   (export "RUBYOPT=rubygems")
                                                   (cd "activesupport")
                                                   (rake "-f" "/usr/lib/ruby/gems/1.8/gems/ci_reporter-1.6.5/stub.rake" "ci:setup:testunit" "test"))
                                                  (map-remote-files "/home/arohner/rails/activesupport/test/reports/" (fn [file]
                                                                                                                        (println (slurp file))))
                                                   ;; cd activerecord && rake mysql:build_databases
                                                   ;; sudo -u postgres createuser --superuser $USER
                                                   ;; rake postgresql:build_databases
                                                   
                                                   )))