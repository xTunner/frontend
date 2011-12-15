(ns circle.integration.production
  (:require [circle.sh :as sh])
  (:require [clojure.java.io :as io])
  (:use [robert.bruce :only (try-try-again)])
  (:require [clj-http.client :as http])
  (:require [circle.util.posix :as posix])
  (:use midje.sweet))

;;
;; This test is currently not working because we're having difficulty killing the trinidad process at the end of the test.
;;

;; (fact "the server starts up in production mode"
;;   (let [success? (atom nil) ;; nil = unknown. false = fail
;;         process (sh/process "trinidad &> trinidad.log; echo $! > trinidad.pid"
;;                             :environment {"RAILS_ENV" "production"
;;                                           ;; unset swank in case the current process is using it
;;                                           "CIRCLE_SWANK" "''"}
;;                             :pwd "")

;;         t (Thread. (fn []
;;                      (let [exit (.waitFor process)]
;;                        (println "server exited:" exit
;;                                 ;; java is retarded, getInputStream actually returns stdout, not stdin.
;;                                 (-> process .getInputStream (slurp))
;;                                 (-> process .getErrorStream (slurp))))
;;                      ;; if we got here, the process exited, and the test fails.
;;                      (println "setting success=false")
;;                      (swap! success? (constantly false))))]
;;     (.start t)
;;     (try-try-again
;;      {:sleep 5000
;;       :tries (* 12 5)
;;       :error-hook (fn [e]
;;                     (if (nil? @success?)
;;                       nil
;;                       false))}
;;      (fn []
;;        (let [resp (http/get "http://localhost:3000")]
;;          (when (= 200 (-> resp :status))
;;            (swap! success? (constantly true))))))

;;     @success? => true
;;     (let [trinidad-pid (Integer/parseInt (slurp "trinidad.pid"))]
;;       (println "trinidad-pid= " trinidad-pid))
;;     ;; (posix/kill :signal 9)
;;     ))

