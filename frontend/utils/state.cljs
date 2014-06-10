(ns frontend.utils.state
  (:require [frontend.state :as state]
            [frontend.utils.vcs-url :as vcs-url]))

(defn reset-current-build [state]
  (assoc state :current-build-data {:build nil
                                    :usage-queue-data {:builds nil
                                                       :show-usage-queue false}
                                    :artifact-data {:artifacts nil
                                                    :show-artifacts false}
                                    :current-container-id 0
                                    :container-data {:current-container-id 0
                                                     :containers nil}}))

(defn reset-current-project [state]
  (assoc state :current-project-data {:project nil
                                      :plan nil
                                      :settings {}
                                      :tokens nil
                                      :envvars nil}))

(defn stale-current-project? [state project-name]
  (and (get-in state state/project-path)
       ;; XXX: check for url-escaped characters (e.g. /)
       (not= project-name (vcs-url/project-name (get-in state (conj state/project-path :vcs_url))))))
