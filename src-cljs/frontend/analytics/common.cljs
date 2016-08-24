(ns frontend.analytics.common
  (:require [schema.core :as s]))

(def UserProperties
  {:all_emails [s/Str]
   :basic_email_prefs s/Str
   :bitbucket_authorized s/Bool
   :created_at s/Str
   :in_beta_program s/Bool
   :login s/Str
   :name s/Str
   :selected_email (s/maybe s/Str)
   :sign_in_count s/Int})
