(ns frontend.components.pages.user-settings.integrations
  (:require [frontend.components.pieces.card :as card]
            [frontend.models.user :as user]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [html]]))

(defui Subpage
  static om-next/IQuery
  (query [this]
    [{:app/current-user [{:user/identities [:identity/type :identity/login]}]}])
  Object
  (render [this]
    (let [{[github-identity] "github"
           [bitbucket-identity] "bitbucket"}
          (group-by :identity/type
                    (-> (om-next/props this) :app/current-user :user/identities))]
      (card/collection
       [(card/titled {:title "GitHub"}
                     (html
                      [:div
                       [:p "Build and deploy your GitHub repositories."]
                       (if github-identity
                         [:p "Connected to " (:identity/login github-identity) "."]
                         [:p "Not connected."])]))
        (card/titled {:title "Bitbucket"}
                     (html
                      [:div
                       [:p "Build and deploy your Bitbucket repositories."]
                       (if bitbucket-identity
                         [:p "Connected to " (:identity/login bitbucket-identity) "."]
                         [:p "Not connected."])]))]))))
