(ns circle.util.test-retry
  (:use midje.sweet)
  (:use circle.util.retry)
  (:require [clj-time.core :as time])
  (:use [circle.util.time :only (to-millis)])
  (:use [circle.test-utils :only (stateful-fn)])
  (:import java.io.IOException))

(defn foo []
  (println "foo"))

(fact "wait-for retries"
  (wait-for {:sleep (time/millis 1)
             :tries 10}
            (fn []
              (foo))) => true
              (provided
                (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for throws on timeout"
  (wait-for {:sleep (time/millis 1)
             :tries 10}
            (fn []
              (foo))) => (throws Exception)
              (provided
                (foo) => false :times 10))

(fact "wait-for works with an error hook"
  (wait-for {:sleep (time/millis 1)
             :tries 10
             :error-hook (fn [e]
                           (printf (format "caught: %s\n" (.getMessage e))))}
            (fn []
              (foo))) => true
              (provided
                (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for works with a success fn"
  (wait-for {:sleep (time/millis 1)
             :tries 10
             :success-fn (fn [v]
                           (= v 8))}
            (fn []
              (foo))) => 8
              (provided
                (foo) =streams=> (range) :times 9))

(fact "timeout works"
  (wait-for {:sleep (time/millis 1)
             :tries 10
             :timeout (time/millis 400)
             :success-fn (fn [v]
                           (= v 42))}
            (fn []
              (Thread/sleep 100)
              (foo))) => (throws Exception)
              (provided
                (foo) =streams=> (range) :times 4))

(fact "throws when f fails to become ready"
  (fact "timeout works"
  (wait-for {:sleep (time/millis 1)
             :timeout (time/millis 20)}
            (fn []
              (foo))) => (throws Exception)
              (provided
                (foo) => false)))

(fact "retries on exception"
  (wait-for {:tries 4
             :sleep (time/millis 1)
             :catch [IOException]}
            (stateful-fn (throw (IOException.))
                         (throw (IOException.))
                         (throw (IOException.))
                         42)) => 42)

(fact "catches listed exceptions"
  (wait-for {:tries 4
             :sleep (time/millis 1)
             :catch [IOException]}
            (stateful-fn (throw (ArrayIndexOutOfBoundsException.)))) => (throws ArrayIndexOutOfBoundsException))

(fact "supports error-hook"
  (wait-for {:tries 4
             :sleep (time/millis 1)
             :catch [IOException]
             :error-hook (fn [e] (foo))}
            (fn []
              (throw (IOException.)))) => (throws IOException)
  (provided
    (foo) => anything :times 4))

(fact "supports unlimited retries"
  (wait-for {:tries :unlimited
             :sleep (time/millis 1)
             :timeout (time/millis 100)}
            (fn []
              (foo))) => (throws Exception)
  (provided
    (foo) => false :times #(> % 40)))

(fact "supports nil sleep"
  (wait-for {:tries 3
             :sleep nil}
            (fn []
              (foo))) => (throws Exception)
  (provided
    (foo) => false :times 3))

(fact ":no-throw works"
  (wait-for {:tries 3
             :sleep nil
             :catch [IOException]
             :error-hook (fn [e] (foo))
             :success-fn :no-throw}
            (stateful-fn (throw (IOException.))
                         (throw (IOException.))
                         nil)) => nil
  (provided
    (foo) => anything :times 2))

(fact "tries not used when sleep and timeout are specified"
  (wait-for {:sleep (time/millis 1)
             :timeout (time/millis 500)}
            foo) => (throws Exception)
  (provided
    (foo) => false :times #(> % 100)))