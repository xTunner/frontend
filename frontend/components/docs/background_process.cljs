(ns frontend.components.docs.background-process)

(def article
  {:title "Start background processes from circle.yml"
   :last-updated "Feb 5, 2014"
   :url :background-process
   :content [:div
             [:p
              "Starting a background process from"
              [:a {:href "/docs/configuration"} "circle.yml"]
              "is entirely possible, but it is not done by adding"
              [:code "&"]
              "to the end of your command line.   Instead, you set the background flag on the command.  For example:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’machine:  post:    - ./daemon:        background: true‘"]
              "’‘"]
             [:p
              "If your server takes more than a moment to start, it might be worth adding a"
              [:code "sleep"]
              "to prevent problems when the tests start:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’machine:  post:    - ./daemon:        background: true    - sleep 5‘"]
              "’‘"]
             [:p
              "Or better yet, block at the start of your test suite until theconnection is available.  This speeds up your tests by starting themin parallel with your daemon, but doesn't suffer from any raceconditions where your tests start before your daemon does."]
             [:p
              "If you do not redirect stdout and stderr in the command, they are capturedinto files in the artifact directory.  (e.g., \\stdout_daemon_7452.txt\\)"]
             [:p
              "(Explanation: The background flag prevents your process being killedin the remote environment.  If a process is running (even in thebackground) when the remote connection is closed, it will get killedby the hangup (HUP) signal.  The background flag uses the \\nohup\\command to prevent this.  The command that gets issued is similar to"
              [:code "nohup bash -c \\./daemon &\\"]
              ")"]]})
