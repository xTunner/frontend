(ns frontend.components.docs.ruby-debugger-problems)

(def article
  {:title "The Ruby debugger gem won't build"
   :last-updated "Dec 20, 2013"
   :url :ruby-debugger-problems
   :content [:div
             [:p
              "The Ruby debugger gem builds native extensions and uses the"
              [:a
               {:href "https://github.com/cldwalker/debugger-ruby_core_source"}
               "debugger-ruby_core_source gem"]
              "to provide the Ruby headers it needs to compile them."]
             [:p
              "Unfortunately debugger-ruby_core_source doesn't include headers for everyversion of Ruby so debugger can only be used with a"]
             [:p
              "You can recognise when debugger doesn't support your version of Ruby if you see a line similar to"
              [:code
               "No source for ruby-1.9.2-p320 provided with debugger-ruby_core_source gem."]
              "in your "
              [:code "bundle install"]
              " output."]
             [:h2 "Interaction with the CircleCI cache"]
             [:p
              "Sometimes people will update their version of Ruby to a version that doesn'twork with debugger, and not experience any problems because the compilednative extensions are still in their CircleCI cache."]
             [:p
              "Then the old cache will go away (happens periodically, or through manualintervention), and suddenly the build doesn't work anymore."]
             [:h2 "Solutions"]
             [:p "There are two ways to get builds working again"]
             [:ul
              [:li "Switch to a version of Ruby that's supported by debugger."]
              [:li "Stop using the debugger gem."]]
             [:p
              "For the adventurous amongst you you can put debugger into a group in yourGemfile and then excluding that group in your "
              [:code "bundle install"]
              "command."]
             [:p "E.g.:"]
             [:p "In your Gemfile:"]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  gem \\debugger\\, :groups => [:development]  ‘"]
              "’‘"]
             [:p "And in your " [:code "circle.yml"]]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  dependencies:  bundler:    without:      - development  ‘"]
              "’‘"]
             [:p
              "This won't work if you've specifically required debugger in your applicationcode, in that case you'll have to use one of the "]]})

