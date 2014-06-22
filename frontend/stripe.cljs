(ns frontend.stripe
  (:require [frontend.async :refer [put!]]
            [frontend.env :as env]
            [goog.net.jsloader]))

;; We may want to add StripeCheckout to externs to avoid all of the aget noise.

(def stripe-key
  (if (env/production?)
    "pk_ZPBtv9wYtkUh6YwhwKRqL0ygAb0Q9"
    "pk_Np1Nz5bG0uEp7iYeiDIElOXBBTmtD"))

(def checkout-defaults {:key stripe-key
                        :name "CircleCI"
                        :address false
                        :panelLabel "Pay"})

(defn open-checkout
  "Opens the StripeCheckout modal, then puts the result of the token callback into channel"
  [{:keys [price description] :as checkout-args} channel]
  (let [checkout (aget js/window "StripeCheckout")
        args (merge checkout-defaults
                    checkout-args
                    ;; n.b. this is a rare case where we put something into a channel
                    ;; that we don't care about serializing.
                    {:token #(put! channel [:stripe-checkout-succeeded %])
                     :closed #(put! channel [:stripe-checkout-closed])})]
    ((aget checkout "open") (clj->js args))))

(defn checkout-loaded?
  "Tests to see if the StripeCheckout javascript has loaded"
  []
  (aget js/window "StripeCheckout"))

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))
