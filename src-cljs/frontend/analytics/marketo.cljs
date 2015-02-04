(ns frontend.analytics.marketo
  (:require [frontend.utils.ajax :as ajax]))

(defn submit-munchkin-form
  "Submits the marketo form with a fallback to /about/contact"
  [form-id params]
  (ajax/managed-form-post "/about/contact"
                          :params {:email (:Email params)
                                   :message params}))
