(ns frontend.components.docs.permissions-and-access-during-deployment)

(def article
  {:title "Permissions and access during deployment"
   :last-updated "Jun 4, 2013"
   :url :permissions-and-access-during-deployment
   :content [:div
             [:p
              "We designed our UI so that it is easy for you to upload the SSH keys we needto securely deploy your projects to your production servers."]
             [:p
              "You enter the security information (public key and private key) for deployment on your project's "
              [:b "Project settings > SSH keys"]
              " page. If you leave the "
              [:b "Hostname"]
              " field blank, the key will be used for all hosts."]]})

