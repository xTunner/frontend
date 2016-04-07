(ns frontend.components.test-org-settings
  (:require [cljs.core.async :as async]
            [frontend.test-utils :as test-utils :refer [example-plan is-re]]
            [frontend.components.org-settings :as org-settings]
            [frontend.utils.docs :as doc-utils]
            [frontend.utils.html :refer [hiccup->html-str]]
            [frontend.stefon :as stefon]
            [goog.dom]
            [om.core :as om :include-macros true]
            [frontend.routes :as routes]
            [om.dom :refer (render-to-str)]
            [cljs.test :as test :refer-macros [deftest is testing]]))

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

(deftest overview-page-works
  (let [overview #(test-utils/component->content
                   org-settings/overview
                   {:current-org-data {:plan %1 :name %2}})]
    (testing "free plans mention the free containers"
      (is-re #"Builds will run in a single, free container." (overview (example-plan :free) "circleci"))
      (is-re #"1 container is free" (overview (example-plan :free :paid) "circleci"))
      (is-re #"1 container is free" (overview (example-plan :free :paid :trial) "circleci")))
    (testing "plans have the correct number of containers"
      (is-re #"Builds will run in a single, free container."
             (overview (example-plan :free) "circleci"))
      (is-re #"All Linux builds will be distributed across 4 containers."
             (overview (example-plan :paid) "circleci"))
      (is-re #"All Linux builds will be distributed across 6 containers."
             (overview (example-plan :trial) "circleci"))
      (is-re #"All Linux builds will be distributed across 7 containers."
             (overview (example-plan :free :trial) "circleci"))
      (is-re #"All Linux builds will be distributed across 10 containers."
             (overview (example-plan :trial :paid) "circleci"))
      (is-re #"All Linux builds will be distributed across 5 containers."
             (overview (example-plan :free :paid) "circleci"))
      (is-re #"All Linux builds will be distributed across 11 containers."
             (overview (example-plan :free :paid :trial) "circleci")))
    (testing "trials are described appropriately"
      (is-re #"5 more days" (overview (example-plan :trial) "circleci")))
    (testing "it links to the piggiebacked plans."
      (is-re #"circleci's plan"
             (overview (example-plan :free) "x")))
    (testing "it shows the right amounts for trial and paid, and minds their interactions."
      ;; Trials are currently upper bounds on plans...
      (let [text (overview (example-plan :trial :paid) "circleci")]
        (is-re #"6 of these are provided by a trial" text)
        (is-re #"4 of these are paid." text))
      ;; so we shouldn't describe one if the paid plan is bigger then it.
      (let [text (overview (example-plan :trial :big-paid) "circleci")]
        (is (re-find #"provided by a trial" text))
        (is-re #"60 of these are paid." text)))
    (testing "it tells you if you're grandfathered"
      (is-re #"grandfathered" (overview (example-plan :grandfathered) "circleci")))))
