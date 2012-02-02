(ns circle.backend.build.queue
  (:use [clojure.core.incubator :only (-?>)])
  (:require [circle.queue-mongo :as queue])
  (:require [clj-time.core :as time])
  (:use [circle.util.args :only (require-args)])
  (:require [circle.model.build]))

(def build-queue :builds)

(defn enqueue-build
  "Adds a build to the queue"
  [{:keys [vcs_url vcs_revision]
    :as args}]
  (require-args vcs_url vcs_revision)
  (let [build (circle.model.build/insert! {:vcs_url vcs_url
                                           :vcs_revision vcs_revision
                                           :queued-at (-> (time/now) (.toDate))})]
    (let [resp (queue/enqueue build-queue build)]
      resp)))

(defn run-next-queued-build
  []
  (let [resp (queue/dequeue build-queue)]
    (println resp)
    (-?> resp (first) :body (read-string))))