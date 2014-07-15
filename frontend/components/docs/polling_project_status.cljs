(ns frontend.components.docs.polling-project-status)

(def article
  {:title "Polling project status using CCMenu, CCTray, etc."
   :last-updated "Feb 2, 2013"
   :url :polling-project-status
   :content [:div
             [:p
              "If you would prefer to poll the status of your projects' builds, rather than rely onnotifications, CircleCI offers an implementation of thewhich is the format (originally from CruiseControl) consumed by common CI monitoring tools."]
             [:h3 "Configuration"]
             [:p
              "It should be possible to use any of these tools to poll your CircleCI builds, byconfiguring them with a URL of the form:"
              [:code "https://circleci.com/cc.xml?circle-token=:circle-token"]]
             [:p
              "Or, if you only care about a subset of your projects, you can use per-project URLs:"
              [:code
               "https://circleci.com/gh/:owner/:repo.cc.xml?circle-token=:circle-token"]]
             [:p
              "You can also use per-branch URLs:"
              [:code
               "https://circleci.com/gh/:owner/:repo/tree/:branch.cc.xml?circle-token=:circle-token"]]
             [:h3 "Access Control"]
             [:p
              "We recommend creating specific, narrowly scoped API tokens. In fact, we created a wholeclass of tokens just for this purpose: tokens which give read-only access to the buildstatus of a single project, and nothing else. You can create and manage these tokens fromthe "
              [:b "Edit settings > API Tokens"]
              " page of any project."]
             [:p
              "If you prefer, it is also possible to use a user's API token as the circle-token. Thesetokens are very powerful, so only do that in a trusted environment!"]
             [:h3 "Tools"]
             [:p
              "Here are some of the desktop tools we support through this standard:"]
             [:ul
              [:li "an OS X menu bar plugin."]
              [:li "a GNU/Linux system tray plugin."]
              [:li "a Windows system tray plugin."]]
             [:p "And browser plugins we support through this plugin:"]
             [:ul [:li "a Firefox plugin."]]
             [:h3 "Notes"]
             [:p
              "If you're using CCMenu, you may have to append"
              [:code "&ccmenu=cc.xml"]
              "(or anything that ends with xml) to the end of your url."]
             [:p
              "If you run into trouble configuring these, or are using a different tool not on this list,please"]]})

