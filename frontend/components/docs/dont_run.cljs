(ns frontend.components.docs.dont-run)

(def article
  {:title "Skip code which should not run on your CI server"
   :last-updated "Feb 2, 2013"
   :url :dont-run
   :content [:div
             [:p
              "If there is code that should not be run on your CI server, you can wrap it in a conditional statement in your code base.CircleCI has set the"
              [:code "CI"]
              "environment variable, which can be used from within your application."]
             [:pre "if !ENV['CI']\n\tDebugger.initialize!\nend"]]})
