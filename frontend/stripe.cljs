(ns frontend.stripe
  (:require [frontend.async :refer [put!]]
            [frontend.env :as env]
            [goog.net.jsloader]))

(def stripe-key
  (if (env/production?)
    "pk_ZPBtv9wYtkUh6YwhwKRqL0ygAb0Q9"
    "pk_Np1Nz5bG0uEp7iYeiDIElOXBBTmtD'"))

(def checkout-defaults {:key stripe-key
                        :name "CircleCI"
                        :address false
                        :panelLabel "Pay"})

(defn open-checkout [{:keys [price description channel]}]
)

(defn checkout-loaded?
  "Tests to see if the StripeCheckout javascript has loaded"
  []
  (aget js/window "StripeCheckout"))

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))
