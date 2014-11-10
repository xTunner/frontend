(ns frontend.proxy
  (:require [org.httpkit.server :refer [with-channel send!]]
            [org.httpkit.client :refer [request]]))

(defn proxy-request [req {:keys [backends] :as options}]
  (let [backend (get backends (:server-name req))]
    (assert backend)
    {:url (str (:proto backend) "://"
               (:host backend)
               (:uri req)
               (when-let [q (:query-string req)]
                 (str "?" q)))
     :timeout 30000 ;ms
     :method (:request-method req)
     :headers (assoc (:headers req)
                     "host" (:host backend)
                     "x-circle-assets-proto" "http"
                     "x-circle-assets-host" (get-in req [:headers "host"]))
     :body (:body req)
     :follow-redirects false}))

(defn wrap-handler [handler options]
  (fn [req]
    (if (some #(re-matches % (:uri req)) (:patterns options))
      (with-channel req channel
        (request (proxy-request req options)
                 (fn [{:keys [status headers body error]}]
                   (if error
                     (send! channel {:status 503
                                     :headers {"Content-Type" "text/plain"}
                                     :body (str "Cannot access backend\n" error)})
                     (send! channel {:status status
                                     :headers (-> (zipmap (map name (keys headers)) (vals headers))
                                                  (dissoc "content-encoding"))
                                     :body body})))))
      (handler req))))
