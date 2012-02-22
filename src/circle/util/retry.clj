(ns circle.util.retry
  (:use [circle.util.except :only (throw-if-not throwf)])
  (:require [clj-time.core :as time])
  (:use [circle.util.time :only (from-now to-millis)])
  (:use [circle.util.predicates :only (instance-pred)])
  (:import org.joda.time.Period))

(instance-pred period? org.joda.time.Period)

(defn parse-args [args]
  (if (map? (first args))
    {:options (first args)
     :f (second args)}
    {:f (first args)}))

(defn unwrap-runtime-exception
  "If Throwable t is a runtime exception, unwrap and return the cause. Otherwise return t"
  [t]
  (if (and (= (class t) java.lang.RuntimeException) (.getCause t))
    (.getCause t)
    t))

(defn catch?
  "True if we should retry after catching Throwable t"
  [options t]
  (let [throwable-coll (if (instance? Throwable (-> options :catch))
                         [(-> options :catch)]
                         (-> options :catch))]
    (some #(instance? % (unwrap-runtime-exception t)) throwable-coll)))

(defn retry? [options]
  (let [{:keys [end-time tries]} options]
    (cond
     (time/after? (time/now) end-time) false
     (and (integer? tries) (<= tries 1)) false
     :else true)))

(defn success? [options result]
  (let [success-fn (-> options :success-fn)]
    (cond
     (= success-fn :no-throw) true
     success-fn (success-fn result)
     (and result (not success-fn)) true
     :else false)))

(defn fail
  "stuff to do when an iteration fails. Returns new options"
  [options]
  (when (-> options :sleep)
    (Thread/sleep (-> options :sleep (from-now) (to-millis))))
  (update-in options [:tries] (fn [tries]
                                (if (integer? tries)
                                  (dec tries)
                                  tries))))

(defn wait-for* [{:keys [options f]}]
  (let [timeout (-> options :timeout)]
    (try
      (let [result (f)]
        (if (success? options result)
          result
          (if (retry? options)
            #(wait-for* {:options (fail options)
                         :f f})
            (throwf "failed to become ready"))))
      (catch Throwable t
        (when-let [hook (-> options :error-hook)]
          (hook t))
        (if (and (catch? options t) (retry? options))
          #(wait-for* {:options (fail options)
                       :f f})
          (throw t))))))

(defn wait-for
  "Like robert bruce, but waits for arbitrary results rather than just
  exceptions.

 - f - a fn of no arguments.

 Options:
 - sleep: how long to sleep between retries, as a joda period. Defaults to 1s.
 - tries: how many times to retry before throwing. Defaults to 10
 - timeout: a joda period. Stop retrying when period has elapsed, regardless of how many tries are left.

 - success-fn: a fn of one argument, the return value of f. Stop
   retrying if success-fn returns truthy. If not specified, wait-for
   returns when f returns truthy. May pass :no-throw here, which will
   return truthy when the f doesn't throw.

 - error-hook: a fn of one argument, an exception. Called when the fn throws."

  {:arglists
   '([fn] [options fn])}
  [& args]
  (let [{:keys [options f] :as parsed-args} (parse-args args)
        {:keys [success timeout tries sleep]
         :or {sleep (time/secs 1)
              tries 10}} options
        tries (if (and sleep timeout (not tries))
                :unlimited
                tries)
        _ (when sleep
            (throw-if-not (period? sleep) "sleep must be a period"))
        end-time (when timeout
                   (throw-if-not (period? timeout) "timeout must be a joda period")
                   (time/plus (time/now) timeout))
        options (-> options
                    (assoc :end-time end-time)
                    (assoc :tries tries)
                    (assoc :sleep sleep))]
    (throw-if-not (-> parsed-args :f fn?) "couldn't find fn")
    (trampoline #(wait-for* {:options options :f f}))))