(ns circleci.model.beta-notify
  (:require [clj-table.user :as table]))

(table/deftable beta-notify
  :tablename "beta_notify"
  :primary-keys [email]
  :columns [email environment features contact session_key])
