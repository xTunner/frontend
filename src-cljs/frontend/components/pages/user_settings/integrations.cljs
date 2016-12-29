(ns frontend.components.pages.user-settings.integrations
  (:require [frontend.components.pieces.card :as card]
            [frontend.models.user :as user]
            [frontend.utils.function-query :as fq :include-macros true]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [html]]))

(defn- card
  {::fq/queries {:identity [:identity/login]}}
  [name identity]
  (card/titled {:title name}
               (html
                [:div
                 [:p "Build and deploy your " name " repositories."]
                 (if identity
                   [:p "Connected to " (:identity/login identity) "."]
                   [:p "Not connected."])])))

(defui Subpage
  static om-next/IQuery
  (query [this]
    [{:app/current-user [{:user/identities (fq/merge [:identity/type]
                                                     (fq/get card :identity))}]}])
  Object
  (render [this]
    (let [{[github-identity] "github"
           [bitbucket-identity] "bitbucket"}
          (group-by :identity/type
                    (-> (om-next/props this) :app/current-user :user/identities))]
      (card/collection
       [(card "GitHub" github-identity)
        (card "Bitbucket" bitbucket-identity)]))))
