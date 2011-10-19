(ns circle.backend.build.run
  (:require [circle.backend.build :as build])
  (:use [circle.backend.nodes :only (node-info)])
  (:use [circle.utils.except :only (throw-if-not)])
  (:use [clojure.tools.logging :only (with-logs error infof)])
  (:use [circle.logging :only (add-file-appender)])
  (:use [circle.backend.action.bash :only (with-pwd)])
  (:use [arohner.utils :only (inspect fold)])
  (:use [circle.backend.action :only (continue? validate-action-result!)])
  (:require [circle.backend.build.email :as email]))

(defn log-ns
  "returns the name of the logger to use for this build "
  [build]
  (str "circle.build." (-> build :project-name) "-" (-> build :build-num)))

(defn log-filename [build]
  (str "build-" (-> build :project-name) "-" (-> build :build-num) ".log"))

(defn run-build [build]
  (infof "starting build: %s #%s" (-> build :project-name) (-> build :build-num))
  (let [node (atom nil)
        update-node (fn update-node [build]
                      (when (not (seq @node))
                        (swap! node (fn [_]
                                      (-> build :group (node-info) (first))))))]
    (when (= :deploy (:type build))
      (throw-if-not (:vcs-revision) "version-control revision is required for deploys"))

    (add-file-appender (log-ns build) (log-filename build))
    
    (try
      (with-pwd "" ;; bind here, so actions can set! it
        (println "starting build: %s" build)
        (let [build-result (fold build [act (-> build :actions)]
                             (update-node build)
                             (if (-> build :continue)
                               (let [context {:build build
                                              :action act
                                              :node @node}
                                     _ (println "calling" (-> act :name))
                                     action-result (-> act :act-fn (.invoke context))]
                                 (println "action-result for" (-> act :name) "is:" action-result)
                                 (validate-action-result! action-result)
                                 (-> build
                                     (update-in [:action-results] conj action-result)
                                     (update-in [:continue] (fn [_] (continue? action-result)))))
                               build))]
          (println "build-result: " build-result)
          (email/send-build-email build build-result)))
      (catch Exception e
        (error e (format "caught exception on %s %s" (-> build :project-name) (-> build :build-num)))
        (email/send-build-error-email build e)))))