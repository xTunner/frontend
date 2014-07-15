(ns frontend.components.docs.cant-follow)

(def article
  {:title "Why can I not follow a project?"
   :last-updated "Feb 2, 2013"
   :url :cant-follow
   :content [:div
             [:p
              "GitHub requires admin permissions to add an SSH key to aproject, and to add the webhook that tells CircleCI when someonepushes new code. If you're not an admin, invite a user withpermissions to CircleCI, and have them follow the project first."]]})
