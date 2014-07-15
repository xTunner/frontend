(ns frontend.components.docs.github-security-ssh-keys)

(def article
  {:title "GitHub security and SSH keys"
   :last-updated "May 1, 2013"
   :url :github-security-ssh-keys
   :content [:div
             [:p
              "GitHub has two different SSH keys—a "
              [:i "deploy"]
              " key and a "
              [:i "user"]
              " key.When you add a GitHub repository to CircleCI, we automatically add a deploykey that references this repository.For most customers, this is all CircleCI needs to run their tests."]
             [:p
              "Each deploy key is valid for only "
              [:i "one"]
              " repository.In contrast, a GitHub user key has access to "
              [:i "all"]
              " of your GitHub repositories."]
             [:p
              "If your testing process refers to multiple repositories(if you have a Gemfile that points at a  private"
              [:code "git"]
              "repository, for example),CircleCI will be unable to check out such repositories with only a deploy key.When testing requires access to different repositories, CircleCI will need a GitHub user key."]
             [:p
              "You can provide CircleCI with a GitHub user key on your project's"
              [:strong "Edit settings > GitHub user"]
              "page.CircleCI creates and associates this new SSH key with your GitHub user accountand then has access to all your repositories."]
             [:h2#security "User key security"]
             [:p
              "CircleCI is serious when it comes to security.We will never make your SSH keys public."]
             [:p
              "Remember that SSH keys should be shared only with trusted users.Anyone that is a GitHub collaborator on a project employing user keyscan access your repositories as you.Beware of someone stealing your code."]
             [:h2#error-messages "User key access-related error messages"]
             [:p
              "Here are common errors that indicate you need to add a user key."]
             [:p [:b "Python"] ": During the" [:code "pip install"] "step:"]
             [:pre
              "’‘"
              [:code.no-highlight "’ERROR: Repository not found.‘"]
              "’‘"]
             [:p [:b "Ruby"] ": During the" [:code "bundle install"] "step:"]
             [:pre
              "’‘"
              [:code.no-highlight "’Permission denied (publickey).‘"]
              "’‘"]
             [:p]]})

