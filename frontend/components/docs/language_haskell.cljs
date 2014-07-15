(ns frontend.components.docs.language-haskell)

(def article
  {:title "Continuous integration and Deployment with Haskell"
   :short-title "Haskell"
   :last-updated "April 17, 2014"
   :url :language-haskell
   :content [:div
             [:p
              "Circle supports building Haskell applications with GHC and Cabal. Before eachbuild we look at your repository and infer commands to run, so mostsetups should work automatically."]
             [:p
              "If you'd like something specific that's not being inferred,"
              [:a {:href "/docs/configuration"} "you can say so"]
              "with"
              [:a {:href "/docs/config-sample"} "a configuration file"]
              "checked into the root of your repository."]
             [:h3 "Version"]
             [:p
              "Circle has"
              [:a {:href "/docs/environment#haskell"} "several versions of GHC"]
              "available. We use"
              [:code " + ($e($c(CI.Versions.ghc))) + "]
              "as the default; if you'd like a particular version, youcan specify it in your "
              [:code "circle.yml"]
              ":"]
             [:h3 "Dependencies & Tests"]
             [:p
              "If we find a Cabal file at the root of your repository, we install yourdependencies and run "
              [:code "cabal build"]
              "and "
              [:code "cabal test"]
              ".You can customize this easily in your "
              [:code "circle.yml"]
              " by settingthe "
              [:code "override"]
              ", "
              [:code "pre"]
              ", and "
              [:code "post"]
              " propertiesin the"
              [:code
               "’  "
               [:a
                {:href "/docs/configuration#dependencies"}
                "’  dependencies  ‘"]
               "‘"]
              "and "
              [:code
               "’  "
               [:a {:href "/docs/configuration#test"} "’  test  ‘"]
               "‘"]
              "sections."]
             [:pre
              "’‘"
              [:code.no-highlight "’test:  post:    - cabal bench‘"]
              "’‘"]
             [:p
              "Circle can"
              [:a
               {:href "/docs/configuration#cache-directories"}
               "cache directories"]
              "in between builds to avoid unnecessary work. If you use Cabal, our inferredcommands build your project in a Cabal sandbox and cache the sandbox.This helps your build run as quickly as possible."]
             [:h3 "Artifacts"]
             [:p "Circle supports saving and uploading arbitrary"]
             [:p
              "If you'd like to automatically generate documentation with Haddock,you can put something like this in your "
              [:code "circle.yml"]
              ":"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’test:  post:    - cabal haddock --builddir=$CIRCLE_ARTIFACTS‘"]
              "’‘"]
             [:h3 "Troubleshooting"]
             [:p "If you have any trouble, pleaseWe'll be happy to help!"]]})

