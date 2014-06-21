(ns frontend.stefon
  "Hacks to replicate stefon's .ref helpers"
  (:require [frontend.utils :as utils :include-macros true]))

(defn data-uri
  "Returns the data-uri version of the image for the given url.
   Image must be specified in resources/assets/js/stefon-hack-for-om.coffee.ref!"
  [url]
  (let [uri-string (-> js/window
                       (aget "stefon_hack_for_om")
                       (aget "data_uris")
                       (aget url))]
    (if uri-string
      uri-string
      (utils/mwarn "Unable to find data-uri for" url
                   "Is it defined in resources/assets/js/stefon-hack-for-om.coffee.ref?"))))
