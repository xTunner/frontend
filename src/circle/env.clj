(ns circle.env)

(def env (if (= (System/getenv "CIRCLE_ENV") "production")
           :production
           :test))

(def production? (= env :production))