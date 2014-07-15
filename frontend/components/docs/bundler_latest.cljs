(ns frontend.components.docs.bundler-latest)

(def article
  {:title "Do you need the latest version of Bundler?"
   :last-updated "Feb 3, 2013"
   :url :bundler-latest
   :content [:div
             [:p
              "CircleCI typically tracks the latest stable version of bundler.Your project may occasionally need to use a pre-release version, which is easy to add to your build.Just add the following to your"
              [:a {:href "/docs/configuration"} "circle.yml"]
              "file."]
             [:pre
              "’‘"
              [:code.bash
               "’dependencies:  pre:    - gem uninstall bundler    - gem install bundler --pre‘"]
              "’‘"]]})
