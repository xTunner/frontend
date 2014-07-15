(ns frontend.components.docs.requires-admin)

(def article
  {:title "CircleCI requires Admin permissions"
   :last-updated "Feb 3, 2013"
   :url :requires-admin
   :content [:div
             [:p
              "On the Manage Projects screen, CircleCI won't allow you to add aproject, because it requires admin permissions."]
             [:p
              "The reason for this is GitHub requires admin permissions to addan SSH key to the project, which CircleCI requires to checkoutcode, and to add a webhook, so GitHub tells us each time youcommit new code. If you are not admin on a project you wouldlike to follow, you should have an admin follow the projectfirst, then you will be able to follow it."]]})

