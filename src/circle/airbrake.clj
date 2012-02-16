(ns circle.airbrake
  (:require fs)
  (:require [clj-airbrake.core :as airbrake])
  (:require [clj-growl.core :as clj-growl])
  (:use [circle.util.args :only (require-args)])
  (:use [circle.globals :only (*current-build-url* *current-build-number*)])
  (:require [circle.env :as env]))

(def api-key (or (System/getenv "AIRBRAKE_API_KEY") "b345706d1e61712d474d722c09a04dd2"))


(def growl (clj-growl/make-growler "" "Circle" ["Airbrake" true]))

(defn airbrake
  "Sends an airbrake notify.
Args:
 env: the current environment. Defaults to the value of circle.env/env
 pwd: the directory the app is running in. Defaults to (fs/cwd)
 exception: the exception that was thrown
 data: a map, arbitrary data include in the report

 force: if true, send the airbrake even if we're not in production"
  [& {:keys [env pwd exception data force]
      :or {env (env/env)
           pwd (fs/cwd)}}]
  (require-args env pwd exception)
  (when exception
    (.printStackTrace exception))
  (growl "Airbrake" "Exception!" (str (when exception
                                          (.getMessage exception))))
  (when (or force (circle.env/staging?) (circle.env/production?))
    (airbrake/notify api-key (env/env) (fs/cwd) exception (merge {:url "http://fakeurl.com"
                                                                  :params (merge data {:build-url *current-build-url*
                                                                                       :build-num *current-build-number*})}))))