(ns frontend.proxy
  (:require [clojure.string :as string]
            [org.httpkit.server :refer [with-channel send!]]
            [org.httpkit.client :refer [request]]))

(defn proxy-request [req {:keys [backends backends-fallback] :as options}]
  (let [backend (or (get backends (:server-name req))
                    (backends-fallback (:server-name req)))]
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
                     "x-circleci-assets-proto" "http"
                     "x-circleci-assets-host" (get-in req [:headers "host"]))
     :body (:body req)
     :follow-redirects false}))

(defn rewrite-error [{:keys [error] :as response}]
  {:status 503
   :headers {"Content-Type" "text/plain"}
   :body (str "Cannot access backend\n" error)})

(defn strip-secure-cookie [header-val]
  (cond (string? header-val) (string/replace header-val #";(\s)*Secure" "")
        (coll? header-val) (map strip-secure-cookie header-val)))

(defn strip-secure [headers]
  (if (headers "set-cookie")
    (update-in headers ["set-cookie"] strip-secure-cookie)
    headers))

(defn rewrite-success
  "Patches up the proxied response with some ugly hacks. Documented within."
  [{:keys [status headers body] :as response}]
  (let [headers (-> (zipmap (map name (keys headers)) (vals headers))
                    ;; httpkit will decode the body, so hide the
                    ;; fact that the backend was gzipped.
                    (dissoc "content-encoding")
                    ;; avoid setting two Dates!  httpkit here will insert another Date
                    (dissoc "date")
                    ;; The production server insists on secure cookies, but
                    ;; the development proxy does not support SSL.
                    strip-secure
                    ;; Silence pagespeed warnings
                    (assoc "Vary" "Accept-Encoding"))]

    {:status status
     :headers headers
     :body body}))

(defn wrap-handler [handler options]
  (fn [req]
    (let [local-response (handler req)]
      (if (not= 404 (:status local-response))
        local-response
        (with-channel req channel
          (request (proxy-request req options)
                   (fn [response]
                     (let [rewrite (if (:error response) rewrite-error rewrite-success)]
                       (send! channel (rewrite response))))))))))
