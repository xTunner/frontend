(ns circle.web.middleware.logging
  (:use [clojure.tools.logging :only (infof)]))

(defn wrap-log [handler]
  (fn [request]
    (let [resp (handler request)]
      (infof "request %s -> %s" (-> request :uri) resp)
      resp)))