(ns frontend.components.docs.ssh-build)

(def article
  {:title "SSH access to builds"
   :last-updated "Feb 2, 2013"
   :url :ssh-build
   :content [:div
             [:p
              "Often the best way to troubleshoot problems is to ssh into arunning or finished build to look at log files, running processes,and so on."]
             [:p "You can do that with CircleCI!"]
             [:p
              "Near the upper right corner of each build page, you'll find ablue 'SSH' button:"]
             [:img {:src " + ($c(assetPath(/img/docs/ssh-build-button.png))) + "}]
             [:p
              "Clicking this button will start a new build with remote SSHaccess enabled. After a few moments on the new build, you'llsee a section labeled 'Enable SSH'. Inside this section,you will find the host and port information:"]
             [:img
              {:src " + ($c(assetPath(/img/docs/ssh-build-details.png))) + "}]
             [:p
              "Now you can ssh to the running build (using the same ssh keythat you use for GitHub) to perform whatever troubleshootingyou need to."
              [:b "Your build commands will run as usual."]]
             [:p
              "After the build commands run, the build output will show anotherspecial section labeled 'Wait for SSH', which repeats the host andport information."]
             [:p
              "The build VM will remain available for"
              [:b "30 minutes after the build finishes running"]
              "and then automatically shut down. (Or you can cancel it.)"]
             [:h4 "Parallelism and SSH Builds"]
             [:p
              "If your build has parallel steps, we launch more than one VMto perform them. Thus, you'll see more than one 'Enable SSH' and'Wait for SSH' section in the build output."]
             [:h4 "Debugging: \\Permission denied (publickey)\\"]
             [:p
              "If you run into permission troubles trying to ssh to your build, trythese things:"]
             [:h5 "Ensure that you can authenticate with github"]
             [:p
              "Github makes it very easy to test that your keys are setup as expected.Just run:"]
             [:pre
              "’  ‘"
              [:code.bash "’  " [:preserve "  $ ssh git@github.com  "] "  ‘"]
              "’‘"]
             [:p]
             [:div "and you should see:"]
             [:pre
              "’  ‘"
              [:code.bash
               "’  "
               [:preserve
                "  Hi :username! You've successfully authenticated...  "]
               "  ‘"]
              "’‘"]
             [:p
              "If you"
              [:em "don't"]
              "see output like that, you need to start by"]
             [:h5 "Ensure that you're authenticating as the correct user"]
             [:p
              "If you have multiple github accounts, double-check that you areauthenticated as the right one! Again, using github's ssh service,run ssh git@github.com and look at the output:"]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  "
               [:preserve
                "  Hi :username! You've successfully authenticated...  "]
               "  ‘"]
              "’‘"]
             [:p
              "In order to ssh to a circle build, the username must be one which hasaccess to the project being built!"]
             [:p
              "If you're authenticating as the wrong user, you can probably resolve thisby offering a different ssh key with ssh -i. See the next section ifyou need a hand figuring out which key is being offered."]
             [:h5 "Ensure that you're offering the correct key to circle"]
             [:p
              "If you've verified that you can authenticate with github as the correctuser, but you're still getting \\Permission denied\\ from CircleCI, youmay be offering the wrong credentials to us. (This can happen forseveral reasons, depending on your ssh configuration.)"]
             [:p
              "Figure out which key is being offered to github that authenticates you, byrunning:"]
             [:pre
              "’  ‘"
              [:code.bash "’  " [:preserve "  $ ssh -v git@github.com  "] "  ‘"]
              "’‘"]
             [:p "In the output, look for a sequence like this:"]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  "
               [:preserve
                "  debug1: Offering RSA public key: /Users/me/.ssh/id_rsa_github  "
                [:_... "  debug1: Authentication succeeded (publickey).  "]]
               "  ‘"]
              "’‘"]
             [:p
              "This sequence indicates that the key /Users/me/.ssh/id_rsa_github is the one whichgithub accepted."]
             [:p
              "Next, run the ssh command for your circle build, but add the -v flag.In the output, look for one or more lines like this:"]
             [:pre
              "’  ‘"
              [:code.no-highlight
               "’  "
               [:preserve "  debug1: Offering RSA public key: ...  "]
               "  ‘"]
              "’‘"]
             [:p
              "Make sure that the key which github accepted (in ourexample, /Users/me/.ssh/id_rsa_github) was also offered to CircleCI."]
             [:p
              "If it was not offered, you can specify it via the -i command-lineargument to ssh. For example:"]
             [:pre
              "’  ‘"
              [:code.bash
               "’  "
               [:preserve
                "  $ ssh -i /Users/me/.ssh/id_rsa_github -p 64784 ubuntu@54.224.97.243  "]
               "  ‘"]
              "’‘"]
             [:h5 "Nope, still broken"]
             [:p "Drat! Well, + $c(HAML['contact_us']())and we'll try to help."]]})

