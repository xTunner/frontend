(ns frontend.env)

(def ^:dynamic env-var nil)

(defn env []
  (or env-var (-> js/window (aget "renderContext") (aget "env") keyword)))

(defn production? []
  (= (env) :production))

(defn staging? []
  (= (env) :staging))

(defn test? []
  (= (env) :test))

(defn development? []
  (= (env) :development))
