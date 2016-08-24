(ns frontend.models.test-user
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.models.user :as user]))

(deftest primary-email-works
  (let [selected-email "selected@email.com"
        email "public@profile.com"
        all-emails ["all@emails.com" "last@emails.com"]
        user {:selected_email selected-email
              :all_emails all-emails}]

   (testing "if they have a :selected-email we use that"
     ;; The user should always have a :selected_email since we insert the primary-email into
     ;; that key in the api layer.
    (is (= selected-email (user/primary-email user))))

   (testing "otherwise its nil"
     (is (= nil (user/primary-email (-> user
                                        (dissoc :selected_email))))))))
