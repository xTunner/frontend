(ns frontend.components.test-org-settings
  (:require [cemerick.cljs.test :as t]
            [dommy.core :as dommy]
            [frontend.test-utils :as test-utils]
            [frontend.components.org-settings :as org-settings]
            [frontend.utils.docs :as doc-utils]
            [frontend.utils.html :refer [hiccup->html-str]]
            [frontend.stefon :as stefon]
            [goog.dom]
            [om.core :as om :include-macros true])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)]
                   [dommy.core :refer (sel1)]))

(deftest test-discount-rendering
  (let [format (fn [plan] (.-innerText (goog.dom/htmlToDocumentFragment (hiccup->html-str (org-settings/format-discount plan)))))
        fifty-percent {:discount { :coupon {
                                     :id                 "qJayHMis"
                                     :percent_off        1
                                     :amount_off         nil
                                     :duration           "forever"
                                     :duration_in_months nil
                                     :valid              true }}}
        sweety-high {:discount {:coupon {
                                     :max_redemptions 1,
                                     :valid false,
                                     :amount_off nil,
                                     :duration_in_months 3,
                                     :created 1390517901,
                                     :duration "repeating",
                                     :redeem_by nil,
                                     :currency nil,
                                     :percent_off 15,
                                     :id "sweety-high-discount",
                                     :times_redeemed 1,
                                     :livemode true,
                                     :metadata {},
                                     :object "coupon"}}}
        blog-post {:discount {:coupon {
                                     :max_redemptions nil,
                                     :valid true,
                                     :amount_off 10000,
                                     :duration_in_months 1,
                                     :created 1406670715,
                                     :duration "repeating",
                                     :redeem_by nil,
                                     :currency "usd",
                                     :percent_off nil,
                                     :id "blog-post",
                                     :times_redeemed 1,
                                     :livemode true,
                                     :metadata {},
                                     :object "coupon"}}}]

    (is (=  "Your plan includes 1% off forever from coupon code qJayHMis"
            (format fifty-percent)))
    (is (=  "Your plan includes $100.00 off for 1 month from coupon code blog-post"
            (format blog-post)))
    (is (=  "Your plan includes 15% off for 3 months from coupon code sweety-high-discount"
            (format sweety-high)))))
