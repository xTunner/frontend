(ns frontend.components.docs.capybara-timeout)

(def article
  {:title "Unable to obtain stable firefox connection in 60 seconds"
   :last-updated "Feb 2, 2013"
   :url :capybara-timeout
   :content [:div
             [:p
              "This is an issue with the selenium-webdriver gem.As Firefox updates to newer revisions, the interface used by selenium-webdriver can break.Fortunately, the fix is pretty easy."]
             [:p
              "Update to a new version of the selenium-webdriver gem in your Gemfile, if possible to the latest version.There are known issues with using anything older than version 2.32.0 of this gem.Updating to the"
              [:a
               {:href "http://rubygems.org/gems/selenium-webdriver"}
               "latest revision"]
              "is probably your best bet."]]})
