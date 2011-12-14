(ns circle.debug)

(defn get-all-threads
  "Returns a seq of every running thread"
  []
  (->
   (Thread/getAllStackTraces)
   (keys)))

(defn get-all-user-threads
  "Returns a seq of all user (non-daemon) threads"
  []
  (->> (get-all-threads)
       (remove #(.isDaemon %))))

(defn print-stack [thread]
  (let [e (Exception.)]
    (println "Thread" (.getName thread))
    (.setStackTrace e (.getStackTrace thread))
    (.printStackTrace e *out*)))

(defn print-all-user-threads []
  (doseq [t (get-all-user-threads)]
    (print-stack t)))

