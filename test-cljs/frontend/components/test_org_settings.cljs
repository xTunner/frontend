(ns frontend.components.test-org-settings
  (:require [cemerick.cljs.test :as t]
            [cljs.core.async :as async]
            [frontend.test-utils :as test-utils]
            [frontend.components.org-settings :as org-settings]
            [frontend.utils.docs :as doc-utils]
            [frontend.utils.html :refer [hiccup->html-str]]
            [frontend.stefon :as stefon]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [goog.dom]
            [om.core :as om :include-macros true]
            [frontend.routes :as routes]
            [om.dom :refer (render-to-str)])
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var]]
                   [frontend.test-macros :refer [is-re]]))

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

(defn example-plan [& keys]
  (as-> {:trial {:trial {:template {:free_containers 6
                                    :id "t1"
                                    :price 0
                                    :type "trial"}}
                 :trial_end (->> 5
                                 (time/days)
                                 (time/from-now)
                                 (time-format/unparse (:basic-date-time time-format/formatters)))}
         :paid {:paid {:template {:id "p18"
                                  :free_containers 0
                                  :price 0
                                  :container_cost 50
                                  :type "containers"}}
                :containers 4}
         :big-paid {:paid {:template {:id "p18"
                                      :free_containers 0
                                      :price 0
                                      :container_cost 50
                                      :type "containers"}}
                    :containers 60}
         :free {:free {:template {:id "f1"
                                  :free_containers 1
                                  :type "free"}}}}
        %
        (map % keys)
        (conj % {:org_name "circleci"})
        (apply merge %)))

(deftest overview-page-works
  ;; TODO: figure out how to set up the routes stuff in tests.
  ;;   (it fails with an error that's totally inscrutable because of closure compilation)
  ;; TODO: should this be in a middleware or something?
  (routes/define-user-routes! (async/chan) true)
  (let [overview #(test-utils/component->content
                   org-settings/overview
                   {:current-org-data {:plan %1 :name %2}})]
    (testing "free plans mention the free containers"
      (is-re #"1 container is free" (overview (example-plan :free) "circleci"))
      (is-re #"1 container is free" (overview (example-plan :free :paid) "circleci"))
      (is-re #"1 container is free" (overview (example-plan :free :paid :trial) "circleci")))
    (testing "plans have the correct number of containers"
      (is-re #"Builds will run in a single container."
             (overview (example-plan :free) "circleci"))
      (is-re #"Builds will be distributed across 4 containers."
             (overview (example-plan :paid) "circleci"))
      (is-re #"Builds will be distributed across 6 containers."
             (overview (example-plan :trial) "circleci"))
      (is-re #"Builds will be distributed across 7 containers."
             (overview (example-plan :free :trial) "circleci"))
      (is-re #"Builds will be distributed across 6 containers."
             (overview (example-plan :trial :paid) "circleci"))
      (is-re #"Builds will be distributed across 5 containers."
             (overview (example-plan :free :paid) "circleci"))
      (is-re #"Builds will be distributed across 7 containers."
             (overview (example-plan :free :paid :trial) "circleci")))
    (testing "trials are described appropriately"
      (is-re #"5 more days" (overview (example-plan :trial) "circleci")))
    (testing "it links to the piggiebacked plans."
      (is-re #"circleci's plan"
             (overview (example-plan :free) "x")))
    (testing "it shows the right amounts for trial and paid, and minds their interactions."
      ;; Trials are currently upper bounds on plans...
      (let [text (overview (example-plan :trial :paid) "circleci")]
        (is-re #"2 of these are provided by a trial" text)
        (is-re #"4 of these are paid." text))
      ;; so we shouldn't describe one if the paid plan is bigger then it.
      (let [text (overview (example-plan :trial :big-paid) "circleci")]
        (is (not (re-find #"provided by a trial" text)))
        (is-re #"60 of these are paid." text)))))
