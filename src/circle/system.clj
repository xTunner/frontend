(ns circle.system
  (:require [circle.backend.build.run :as run])
  (:require [circle.model.build :as build])
  (:require [circle.backend.ec2 :as ec2])
  (:use [clojure.tools.logging :only (infof)]))

(defn graceful-shutdown
  "Shutdown there are no more running builds."
  []
  (letfn [(shutdown []
            (infof "graceful shutdown watcher, count=%s builds=%s" (count @run/in-progress) (map build/build-name @run/in-progress))

            (when (zero? (count @run/in-progress))
              (let [id (ec2/self-instance-id)]
                (when id
                  (infof "calling self-terminate on %s" id)
                  (ec2/terminate-instances! id)))))]
    (infof "adding graceful shutdown watcher")
    (add-watch run/in-progress :shutdown (fn [key in-progress old-state new-state]
                                           (shutdown)))
    (shutdown)))

(defn stop-shutdown []
  (infof "removing graceful shutdown watcher")
  (remove-watch run/in-progress :shutdown))