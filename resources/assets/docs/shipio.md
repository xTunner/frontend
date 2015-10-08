<!--

title: Ship.io to CircleCI Migration
short_title: Ship.io

-->

# Ship.io to CircleCI Migration

Coming from Ship.io? We'll help you get started. For iOS projects, please [contact support](mailto:sayhi@circleci.com) with the name of your GitHub user or organization for access to the iOS build system. Once you've been enabled, 

1. Add your project on the [Add Projects page](https://circleci.com/add-projects). 
2. Turn on the "Build iOS project" setting through the **Project Settings > Experimental Settings** page of your your project.
3. Push a new commit to start a build on the iOS build system.

## iOS FAQ: How do I...

### Set environment variables
You can set environment variables through the **Project Settings > Environment Variables** page of your project, or through [circle.yml](https://circleci.com/docs/configuration#environment).

### Use Xcode 7
Include a `circle.yml` file in the repo's root directory with the following contents:

```
machine:
  xcode:
    version: "7.0"
```

### Pick a scheme
If you have more than one shared scheme in your repo, you can specify the name of the scheme you would like to use to run your tests using the `XCODE_SCHEME` environment variable.

### Run scripts
Make sure any scripts that you want to run are included in your repository. You can run your script using a bash command (e.g. `./example_script.sh`) configured in our UI (through **Project Settings > Dependency/Test Commands**) or in a [circle.yml](https://circleci.com/docs/configuration) file.

### Configure build notifications
You can configure build notifications using the "Notifications" section of your Project Settings. Email notifications can be configured from the [Account page](https://circleci.com/account).

### Customize the build commands
You can add to or override our inferred commands through your Project Settings or through a [circle.yml file](https://circleci.com/docs/configuration).

### Deploy my app
We recommend using [Fastlane](https://medium.com/mitoo-insider/how-to-set-up-continuous-delivery-for-ios-with-fastlane-and-circleci-c7dae19df2ed). 

### Get more help
* [iOS build docs](https://circleci.com/docs/ios)
* [discuss.circleci.com](https://discuss.circleci.com/c/mobile)
* [CircleCI Support](mailto:sayhi@circleci.com)