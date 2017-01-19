(ns frontend.components.pages.user-settings.integrations
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.analytics :as analytics]
            [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.models.user :as user]
            [frontend.utils.bitbucket :as bitbucket]
            [frontend.utils.function-query :as fq :include-macros true]
            [frontend.utils.github :as gh-utils]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- card
  {::fq/queries {:identity [:identity/login]}}
  [c {:keys [name type icon-path auth-url identity]}]
  (component
    (card/titled {:title
                  (element :icon
                    (html
                     [:img {:src icon-path
                            :alt name}]))
                  :action (when-not identity
                            (button/link {:href auth-url
                                          :on-click #(analytics/track! c {:event-type :authorize-vcs-clicked
                                                                          :properties {:vcs-type type}})
                                          :kind :primary
                                          :size :medium}
                                         "Connect"))}
                 (element :body
                   (html
                    [:div
                     [:p "Build and deploy your " name " repositories."]
                     [:p.connection-status
                      (if identity
                        (list "Connected to " (:identity/login identity) ".")
                        "Not connected.")]])))))

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
       [(card this {:name "GitHub"
                    :type "github"
                    :icon-path (common/icon-path "brand-github")
                    :auth-url (gh-utils/auth-url)
                    :identity github-identity})
        (card this {:name "Bitbucket"
                    :type "bitbucket"
                    :icon-path (common/icon-path "brand-bitbucket")
                    :auth-url (bitbucket/auth-url)
                    :identity bitbucket-identity})]))))

(dc/do
  (defcard github-card-disconnected
    (card nil {:name "GitHub"
               :type "github"
               :icon-path (common/icon-path "brand-github")
               :auth-url "#"
               :identity nil})
    {}
    {:classname "background-gray"})

  (defcard github-card-connected
    (card nil {:name "GitHub"
               :type "github"
               :icon-path (common/icon-path "brand-github")
               :auth-url (gh-utils/auth-url)
               :identity {:identity/login "a-github-user"}})
    {}
    {:classname "background-gray"})

  (defcard bitbucket-card-disconnected
    (card nil {:name "Bitbucket"
               :type "bitbucket"
               :icon-path (common/icon-path "brand-bitbucket")
               :auth-url (bitbucket/auth-url)
               :identity nil})
    {}
    {:classname "background-gray"})

  (defcard bitbucket-card-connected
    (card nil {:name "Bitbucket"
               :type "bitbucket"
               :icon-path (common/icon-path "brand-bitbucket")
               :auth-url (bitbucket/auth-url)
               :identity {:identity/login "a-bitbucket-user"}})
    {}
    {:classname "background-gray"}))
