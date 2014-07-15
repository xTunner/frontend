(ns frontend.components.docs.api)

(def article
  {:title "CircleCI REST API"
   :last-updated "Aug 23, 2012"
   :url :api
   :content [:div
             [:h2#intro "The CircleCI API"]
             [:p
              "The CircleCI API is a RESTy, fully-featured API that allows you to do almost anything in CircleCI.You can access all information and trigger all actions.The only thing we don't provide access to is billing functions, which must be done from the CircleCI web UI."]
             [:h2 "Getting started"]
             [:ol
              [:li "Add an API token from your"]
              [:li
               "To test it,"
               [:a
                {:target "_blank", :href "/api/v1/me"}
                "view it in your browser"]
               "or call the API using "
               [:code "curl"]
               ":"
               [:pre
                "’    ‘"
                [:code.bash
                 "’    $ curl https://circleci.com/api/v1/me?circle-token=:token    ‘"]
                "’‘"]]
              [:li
               "You should see a response like the following:"
               [:pre
                {:style "overflow:scroll"}
                "’    ‘"
                [:code.no-highlight
                 {:style "overflow:scroll"}
                 "’    {  \\user_key_fingerprint\\ : null,  \\days_left_in_trial\\ : -238,  \\plan\\ : \\p16\\,  \\trial_end\\ : \\2011-12-28T22:02:15Z\\,  \\basic_email_prefs\\ : \\smart\\,  \\admin\\ : true,  \\login\\ : \\pbiggar\\}    ‘"]
                "’‘"]]]
             [:h2#calling "Making calls"]
             [:p
              "All API calls are made in the same way, by making standard HTTP calls, using JSON, a content-type, and your API token.All CircleCI API endpoints begin with"
              [:code "\\https://circleci.com/api/v1/\\"]
              "."]
             [:h2 "Authentication"]
             [:p
              "To authenticate, add an API token using yourTo use the API token, add it to the"
              [:code "circle-token"]
              "query param, like so:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’curl https://circleci.com/api/v1/me?circle-token=:token‘"]
              "’‘"]
             [:h2 "Accept header"]
             [:p
              "If you specify no accept header, we'll return human-readable JSON with comments.If you prefer to receive compact JSON with no whitespace or comments, add the "
              [:code "\\application/json\\ Accept header"]
              ".Using "
              [:code "curl"]
              ":"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’curl https://circleci.com/api/v1/me?circle-token=:token -H \\Accept: application/json\\‘"]
              "’‘"]
             [:h2 "User"]
             [:h2 "Projects"]
             [:h2#recent-builds "Recent Builds Across All Projects"]
             [:h2#recent-builds-project "Recent Builds For a Single Project"]
             [:p
              "You can narrow the builds to a single branch by appending /tree/:branch to the url:"]
             [:p
              [:code.no-highlight
               "https://circleci.com/api/v1/project/:username/:project/tree/:branch"]]
             "The branch name should be url-encoded."
             [:h2#build "Single Build"]
             [:h2#build-artifacts "Artifacts of a Build"]
             [:h2#retry-build "Retry a Build"]
             [:p
              "You can retry a build with ssh by swapping \\retry\\ with \\ssh\\:"]
             [:p
              [:code.no-highlight
               "https://circleci.com/api/v1/project/:username/:project/:build_num/ssh"]]
             [:h2#cancel-build "Cancel a Build"]
             [:h2#clear-cache "Clear Cache"]
             [:h2 "Summary"]
             [:p "All Circle API endpoints begin with"]
             [:dl.dl-horizontal]
             [:dl
              [:dt " + $e($c( + data.method + : / + url))"]
              [:dd " + $e($c(data.description))"]]]})

