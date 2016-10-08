(ns frontend.models.user
  (:require [clojure.set :as set]
            [frontend.datetime :as datetime]
            [goog.string :as gstring]
            goog.string.format))

(defn missing-scopes [user]
  (let [current-scopes (set (:github_oauth_scopes user))]
    (set/union (when (empty? (set/intersection current-scopes #{"user" "user:email"}))
                 #{"user:email"})
               (when-not (contains? current-scopes "repo")
                 #{"repo"}))))

(defn public-key-scope? [user]
  (some #{"admin:public_key"} (:github_oauth_scopes user)))

(defn unkeyword
  "Converts a keyword in to a string without the leading colon. See server-side function of the same name."
  [kw]
  (.substr (str kw) 1))

(defn project-preferences [user]
  (into {} (for [[vcs-url prefs] (:projects user)]
             [(unkeyword vcs-url) prefs])))

(def free? (boolean (some-> js/window
                            (aget "ldUser")
                            (aget "custom")
                            (aget "free"))))

(def support-eligible? (boolean (some-> js/window
                                        (aget "elevSettings")
                                        (aget "support_enabled"))))
(defn has-org? [{:keys [organizations]} org-name vcs-type]
  (some #(and (= org-name (:login %))
              (= vcs-type (:vcs_type %)))
        organizations))

(defn admin-of-org? [{:keys [organizations]} org-name vcs-type]
  (->> organizations
       (filter #(and (= org-name (:login %))
                     (= vcs-type (:vcs_type %))))
       first
       :admin
       boolean))

(defn github-authorized? [user]
  (-> user :github_oauth_scopes empty? not))

(defn bitbucket-authorized? [user]
  (-> user :bitbucket_authorized boolean))


(defn deauthorize-github [user]
  (-> user
      (assoc :github_oauth_scopes nil)))

(defn deauthorize-bitbucket [user]
  (-> user
      (assoc :bitbucket_authorized nil)))

(defn primary-email [user]
  ;; We shove the primary email into :selected_email in the api layer, even
  ;; though we have a :selected-email key in the collection which is different.
  ;; Encapsulating this logic in case we ever want to make the :selected_email on
  ;; the FE == the :selected-email on the BE.
  (:selected_email user))
