(ns frontend.models.test-user
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.models.user :as user]))

(deftest primary-email-works
  (let [selected-email "selected@email.com"
        email "public@profile.com"
        all-emails ["all@emails.com" "last@emails.com"]
        user {:selected_email selected-email
              :email email
              :all_emails all-emails}]

   (testing "if they have selected-email we use that"
    (is (= selected-email (user/primary-email user))))

   (testing "if they don't have a selected-email, we use the :email (super rare, for profiles that have pushed but never signed up)"
    (is (= email (user/primary-email (dissoc user :selected_email)))))

   (testing "when all else fails, use the last email in the :all_email list (github logic we inherited)"
     (is (= (last all-emails) (user/primary-email (-> user
                                                      (dissoc :selected_email)
                                                      (dissoc :email))))))

   (testing "actually, when all else really fails, its nil"
     (is (= nil (user/primary-email (-> user
                                        (dissoc :selected_email)
                                        (dissoc :email)
                                        (dissoc :all_emails))))))))
