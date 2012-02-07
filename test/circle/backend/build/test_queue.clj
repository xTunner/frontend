(ns circle.backend.build.test-queue
  (:use circle.backend.build.queue)
  (:use circle.backend.build.run)
  (:use circle.backend.build.test-utils)
  (:use midje.sweet))

(future-fact "queuing builds works"
  (let [build (minimal-build)]
    (dotimes [i 5]
      (enqueue-build (select-keys @build [:vcs_url :vcs_revision]))) => nil)
  (provided
    (run-build anything) => anything :times 2))
