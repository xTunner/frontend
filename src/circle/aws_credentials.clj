(ns circle.aws-credentials
  (:import com.amazonaws.auth.BasicAWSCredentials))

(def AWS-access-credentials {:user (or
                                    (System/getenv "AWS_USERNAME")
                                    "AKIAIC6QRAEXNRBTJC4A")
                             :password (or
                                        (System/getenv "AWS_PASSWORD")
                                        "VK1MiE9oIX7f9h2mUWXESQFfJPGt+gJHaYZNT70f")})


(def aws-credentials (BasicAWSCredentials. (-> AWS-access-credentials :user)
                                           (-> AWS-access-credentials :password)))
