        [CircleCI](http://addons.heroku.com/circleci) is an [add-on](http://addons.heroku.com) that runs your tests in the cloud with minimal setup from you. Sign in with github and we start running your tests.


###Quick Setup
Set up your continuous integration in 20 seconds, not two days. With one click CircleCI detects test settings for a wide range of web apps, and set them up automatically on our servers.

###Fast Tests
Your productivity relies on fast test results. CircleCI runs your tests faster than your Macbook Pro, EC2, your local server, or any other service.

###Automatic Parallelization
We can automatically parallelize your tests across multiple machines. With up to 4-way parallelization, your test turn-around time can be massively reduced.

###Continuous Deployment
Get code to your customers faster, as soon as the tests pass. CircleCI supports branch-specific deployments, SSH key management and supports any hosting environment using custom commands, auto-merging, and uploading packages.

###CircleCI supports your language and framework. 
Lots of our customers use  Ruby, Python, Java,  Node.js, Clojure and Scala but we support all testing frameworks and languages. Really, we probably already support your language , but, If after you signup your tests aren't running let us know and we'll add support for your language/framework. 

###Deep Customization
Real applications often deviate slightly from standard configurations, so CircleCI does too. Our configuration is so flexible that it's easy to tweak almost anything you need.

###Debug with Ease
When your tests are broken, we help you get them fixed. We auto-detect errors, have great support, and even allow you to SSH into our machines to test manually.

###Smart Notifications
CircleCI intelligently notifies you via email, Hipchat, Campfire and more. You won't be flooded with useless notifications about other people's builds and passing tests, we only notify you when it matters.

###Loving support
We respond to support requests as soon as possible, every day. Most requests get a response responded to within an hour. No-one ever waits more than 12 hours for a response.

###More to come.
At CircleCI we are always listening to our customers for ideas and feedback. If there is a specific feature or configuration ability you need, we want to know.

## Provisioning the add-on

CircleCI can be attached to a Heroku application via the  CLI:

<div class="callout" markdown="1">
A list of all plans available can be found [here](http://addons.heroku.com/circleci).
</div>

    :::term
    $ heroku addons:add circleci
    -----> Adding circleci to sharp-mountain-4005... done, v18 (free)

Once CircleCI has been added navigate to the heroku dashboard for your app and follow the circleci link to go to the circleci site. Sign in with your github credentials (so we can check out your code) and your tests will be running in seconds. 

After installing CircleCI the application should be configured to fully integrate with the add-on.

## Dashboard

<div class="callout" markdown="1">
For more information on the features available within the CircleCI dashboard please see the docs at [https://circleci.com/docs](circleci.com/docs).
</div>

The CircleCI dashboard allows you to see your running tests, specify which projects to build and set up continuous deployment for your app. 

The dashboard can be accessed via the CLI:

    :::term
    $ heroku addons:open circleci
    Opening circleci for sharp-mountain-4005…

or by visiting the [Heroku apps web interface](http://heroku.com/myapps) and selecting the application in question. Select CircleCI from the Add-ons menu.

## Migrating between plans

Use the `heroku addons:upgrade` command to migrate to a new plan.

    :::term
    $ heroku addons:upgrade circleci:startup
    -----> Upgrading circleci:startup to sharp-mountain-4005... done, v18 ($49/mo)
           Your plan has been updated to: circleci:startup

## Removing the add-on

CircleCI can be removed via the  CLI.

<div class="warning" markdown="1">This will destroy all associated data and cannot be undone!</div>

    :::term
    $ heroku addons:remove circleci
    -----> Removing circleci from sharp-mountain-4005... done, v20 (free)

## Support

All CircleCI support and runtime issues should be submitted via on of the [Heroku Support channels](support-channels). Any non-support related issues or product feedback is welcome at [https://circleci.com](https://circleci.com) . We pride ourselves on the quality of the support we provide so get in touch!