(ns frontend.components.docs.wrong-ruby-version)

(def article
  {:title "CircleCI uses the wrong Ruby version"
   :last-updated "Feb 3, 2013"
   :url :wrong-ruby-version
   :content [:div
             [:p
              "We infer your Ruby version from your .rvmrc file, .ruby-version file, or Gemfile.You can also specify it in your"
              [:a {:href "/docs/configuration#ruby-version"} "circle.yml"]
              "file.If you don't do any of the above, we'll use Ruby + $e($c(CI.Versions.default_ruby))orwhichever we think is best.You can"
              [:a
               {:href "/docs/configuration#ruby-version"}
               "control the version"]
              "if we got it wrong."]]})

