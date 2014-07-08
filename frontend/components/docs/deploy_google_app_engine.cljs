(ns frontend.components.docs.deploy-google-app-engine)

(def article
  {:title "Continuous Deployment to Google App Engine"
   :last-updated "July 19, 2013"
   :url :deploy-google-app-engine
   :content [:div
             [:p
              "Setting up continuous deployment to Google App Engine is pretty straightforward. Here'show you do it."]
             [:h2 "Add Google App Engine SDK as a Dependency"]
             [:p
              "First, you have to"
              [:strong "install the SDK on your build VM."]
              "We don't do this by default, because it's very fast (under 10 seconds) and there are manysupported SDK versions to choose from."]
             [:p
              "You'll need to find the download URL for the SDK that you need. The official source forSDK downloads is"]
             [:p
              "This example"
              [:a {:href "/docs/configuration"} "circle.yml"]
              "fragment installs version 1.5.1 of the Python Google App Engine SDK. Modify it todownload the SDK you need:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’dependencies:  pre:    - curl -O https://googleappengine.googlecode.com/files/google_appengine_1.5.1.zip    - unzip -q -d $HOME google_appengine_1.5.1.zip‘"]
              "’‘"]
             [:h2 "Configure Deployment to Google App Engine"]
             [:p
              "With the SDK installed, next you need to"
              [:b "configure continuous deployment."]
              "You may want to read up on configuring"
              [:a
               {:href "/docs/configuration#deployment"}
               "continuous deployment with circle.yml"]
              "in general if your needs are more complex than what's shown in these examples."]
             [:p
              "For the sake of this example, let's deploy the master branch toGoogle App Engine every time the tests are green. The commands differ slightlydepending on which language you're using, but they're all doing basicallythe same thing:"]
             [:h3 "Python"]
             [:p "Using"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’deployment:  appengine:    branch: master    commands:      - echo $APPENGINE_PASSWORD | $HOME/google-appengine/appcfg.py update --email=$APPENGINE_EMAIL --passin .‘"]
              "’‘"]
             [:h3 "Java"]
             [:p "Using"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’deployment:  appengine:    branch: master    commands:      - echo $APPENGINE_PASSWORD | $HOME/appengine-java-sdk/bin/appcfg.sh update --email=$APPENGINE_EMAIL --passin .‘"]
              "’‘"]
             [:h3 "Go"]
             [:p "Using"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’deployment:  appengine:    branch: master    commands:      - echo $APPENGINE_PASSWORD | $HOME/google_appengine/appcfg.py update --email=$APPENGINE_EMAIL --passin .‘"]
              "’‘"]
             [:h3 "Credentials"]
             [:p
              "In all three cases, the deployment command passes an email address and password tothe appcfg command. The credentials are stored in environment variables, which you canmanage through the web UI as described"]
             [:p
              "Python and Go users can also configure it to use non-interactiveoauth2 authentication, instead (The Java SDK's appcfg.sh does not appear to support thisusage.)"]]})
