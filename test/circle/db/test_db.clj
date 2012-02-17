(ns circle.db.test-db
  (:require [midje.sweet :as midje])
  (:require [somnium.congomongo :as mongo])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-time.core :as time])
  (:require [circle.db.migration-lib :as migration-lib])
  (:require [circle.logging])
  (:use [circle.test-utils])
  (:require [clj-time.core :as time]))

(test-ns-setup)

(midje/fact "migration 3 should work"
  (let [cid (circle.util.mongo/object-id)
        sid (circle.util.mongo/object-id)
        candidate {:_id cid}
        safe-start (.toDate (time/date-time 2011 11 11 11 11 11))
        safe {:_id sid :start_time safe-start}
        _ (mongo/insert! :builds candidate)
        _ (mongo/insert! :builds safe)

        start-time1 (.toDate (time/date-time 2012 10 2 22 15 46))
        start-time2 (.toDate (time/date-time 2012 10 2 22 17 46))
        start-time3 (.toDate (time/date-time 2012 10 2 22 19 46))
        times [start-time1 start-time2 start-time3]

        ;; candidate
        logs (map (fn [t] {:start_time t :_build-ref cid}) times)
        _ (doall (map #(mongo/insert! :action_logs %) logs))

        ;; safe - still add the action_logs, test they change nothing
        logs (map (fn [t] {:start_time t :_build-ref sid}) times)
        _ (doall (map #(mongo/insert! :action_logs %) logs))

        m (get @migration-lib/migrations 3)
        _ (migration-lib/run-migration m)

        new-cid (mongo/fetch-one :builds :where {:_id cid})
        new-sid (mongo/fetch-one :builds :where {:_id sid})]

    (-> new-cid :start_time) => start-time1
    (-> new-sid :start_time) => safe-start
    (-> new-cid :migration_version) => 3
    (-> new-sid :migration_version) => nil))