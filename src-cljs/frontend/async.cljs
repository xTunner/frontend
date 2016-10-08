(ns frontend.async
  (:require [cljs.core.async :as async]
            [om.core :as om :include-macros true]))

(def ^:dynamic *uuid* nil)

(defn put! [port val & args]
  (if (and (satisfies? IMeta val) *uuid*)
    (apply async/put! port (vary-meta val assoc :uuid *uuid*) args)
    (apply async/put! port val args)))

(defn raise! [owner val & args]
  (let [c (om/get-shared owner [:comms :controls])]
    (apply put! c val args)))

(defn navigate!
  "Navigate to a URL. This is almost always better accomplished with a link and
  href, but there are cases where we need to send the user directly to a new
  URL."
  [owner path]
  (let [c (om/get-shared owner [:comms :nav])]
    (put! c [:navigate! {:path path}])))
