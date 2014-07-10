(ns frontend.components.docs.what-happens)

(def article
  {:title "What happens when you add a project?"
   :last-updated "May 2, 2013"
   :url :what-happens
   :content [:div
             [:p
              "Generally, pretty much what you'd expect if you were implementing this yourself:"]
             [:ul
              [:li
               "Using the permissions you gave CircleCI when you signed up, we'll add some Github settings to your project:"
               [:ul
                [:li
                 "A "
                 [:b "deploy key"]
                 "—used to check out your project from GitHub"]
                [:li
                 "A "
                 [:b "service hook"]
                 "—used to notify CircleCI when you push to GitHub"]]]
              [:li
               "CircleCI immediately checks your code out onto our machines, infers your settings from your code, and runs your first build."]
              [:li
               "Our inference algorithms look through your dependencies, Gemfile, libraries, and code to figure out how to run your tests.For example, we might find that you have a standard Rails project using Postgres specifications and features, so we'll run:"]]
             [:p]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  bundle installbundle exec rake db:schema:loadbundle exec rspec specbundle exec cucumber  ‘"]
              "’‘"]
             [:p]
             [:ul
              [:li
               "We run your tests on a clean virtual machine every time.This means that:"
               [:ul
                [:li "Your code isn't accessible to other users"]
                [:li "We run your tests freshly each time you push"]]]
              [:li "You can watch your tests update in real-time on"]
              [:li "We'll send you a notification when you're done."]]]})

