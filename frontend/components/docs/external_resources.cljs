(ns frontend.components.docs.external-resources)

(def article
  {:title "Use resources which are not in your repository"
   :last-updated "Feb 2, 2012"
   :url :external-resources
   :content [:div
             [:p "There are a number of techniques to do this:"]
             [:ul
              [:li
               "CircleCI supportsand has advanced SSH key management to let you access multiple repositories from a single test suite.From your project's"
               [:strong "Edit settings > GitHub user"]
               "page, you can add a \\user key\\ with one-click, allowing you access code from multiple repositories in your test suite.Git submodules can be easily set up in your"
               [:code "circle.yml"]
               "file (see"]
              [:li
               "CircleCI's VMs are connected to the internet. You can download dependencies directly while setting up your project, using"
               [:code "curl"]
               "or"]]]})
