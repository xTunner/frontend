(ns circle.backend.build.queue
  (:require [circle.queue :as queue])
  (:require [circle.backend.build]))

(defn enqueue-build
  "Adds a build to the queue"
  [url revision]
  ;; (circle.model.build/insert {:vcs-url url
  ;;                             :vcs-revision revision
  ;;                             :queued-at (time/now)})

  ;; (circle.backend.build/insert! {:start})
  )