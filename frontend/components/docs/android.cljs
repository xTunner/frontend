(ns frontend.components.docs.android)

(def article
  {:title  "Test Android applications"
   :last-updated "March 22, 2013"
   :url :android
   :content [:div
             [:p
              "CircleCI support testing Android applications. The SDK isalready installed on the VM at"
              [:code "\\/usr\\/local\\/android-sdk-linux"]]
             [:p
              "To save space, we don't downloadevery android version, so you'll need to specify the versionsyou use:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  pre:    \\- echo y | android update sdk --no-ui --filter \\android-18\\‘"]
              "’‘"]]})

