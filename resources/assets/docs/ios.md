<!--

title: Test iOS applications
short_title: iOS
last_updated: December 17, 2014

-->

CircleCI now offers Beta support for building and testing iOS (and OSX) projects.
To enable this feature, go to **Project Settings > Experimental Settings** and
enable the "Build iOS project" experimental setting. This will cause builds for
this project to be run on OSX machines rather than the usual Linux containers.


##Basic setup

Simple projects should run with minimal or no configuration. By default, CircleCI will:

* **Install any Ruby gems specified in a Gemfile** - You can install a specific version of CocoaPods or other gems this way
* **Install any dependencies managed by [CocoaPods](http://cocoapods.org/)**
* **Run the "test" build action for detected project(s) and scheme(s) (todo: are these plural?) 
from the command line using `xcodebuild`** - The detected settings can be overridden with [environment variables](#environment-variables)

**Note:** Your scheme (what you select in the dropdown next to the
play/stop buttons in Xcode) must be shared (there is a checkbox for this at the bottom of
the "Edit scheme" screen in Xcode) so that CircleCI can run the appropriate build action.
If more than one scheme is present, then whichever is alphabetically first will be used
(TODO: Is that right?). If you need to use a specific scheme, you can specify the
`XCODE_SCHEME` environment variable from **Project Settings > Environment Variables**.


##Supported build and test tools

CircleCI's automatic commands cover a lot of common test patterns, and you can customize your build
as needed to satisfy almost any iOS build and test strategy.

###XCTest-based tools
In addition to standard `XCTestCase` tests, CircleCI will automatically run tests
written in any other tool that bilds on top of XCTest and is configured to run
via the "test" build action. The following test tools are known to work well on CircleCI
(though many others should work just fine):

* [XCTest](https://developer.apple.com/library/ios/documentation/DeveloperTools/Conceptual/testing_with_xcode/Introduction/Introduction.html)
* [Kiwi](https://github.com/kiwi-bdd/Kiwi)
* [KIF](https://github.com/kif-framework/KIF)

###xctool
While CircleCI runs tests from the command line with the `xcodebuild` command by
default, [xctool](https://github.com/facebook/xctool) is also pre-installed on
CircleCI. For example you could run your build and tests with xctool by adding
the following `circle.yml` file to your project:

(todo: is this true?)
```
test:
  override:
    - xctool -scheme $XCODE_SCHEME -workspace $XCODE_WORKSPACE -sdk iphonesimulator clean test
```

See [customizing your build](#customizing-your-build) for more information about customization options.

###Other tools
Popular iOS testing tools like [Appium](http://appium.io/) and [Frank](http://www.testingwithfrank.com/) should also
work normally, though they will need to be installed and called using custom commands.
See [customizing your build](#customizing-your-build) for more info.


##Customizing your build
While CircleCI's inferred commands will handle many common testing patterns, you also
have a lot of flexibility to customize what happens in your build.

###Environment variables
You can customize the behavior of CircleCI's automatic build commands by setting
the following environment variables in **Project Settings > Environment Variables**:

* **XCODE_WORKSPACE** - The path to your `.xcworkspace` file relative to the git repository root
* **XCODE_PROJECT** - The path to your `.xcodeproj` file relative to the repository root
* **XCODE_SCHEME** - The name of the scheme you would like to use to run the "test" build action

###Configuration file
The most flexible means to customize your build is to add a `circle.yml` file to your project,
which allows you to run arbitrary bash commands instead of or in addition to the inferred commands
at various points in the build process. See the [configuration doc](/docs/configuration) for
a detailed discussion of the structure of the `circle.yml` file. Note, however, that
a number of options, particularly in the `machine` section, may result in errors because
OSX vms feature less pre-installed packages and options than our standard Linux containers.
In such cases you may need run custom commands in appropriate build phases and install
custom packages yourself (see below).

###Custom packages
[Homebrew](http://brew.sh/) is pre-installed on CircleCI, so you can simply use `brew install`
to add nearly any dependency required in your build VM. Here's an example:
```
dependencies:
  pre:
    - brew install cowsay
test:
  override:
    - cowsay Hi!
```

You can also use the `sudo` command if necessary to perform customizations outside of Homebrew.

##Code signing and deployment
You can build a signed app and deploy to various destinations using the customization options
mentioned [above](#customizing-your-build). For an example of a deployment to
[HockeyApp](http://hockeyapp.net/features/), see [this gist](https://gist.github.com/bellkev/cb067710d6d0adf7c678).
(todo: make public or put somewhere else). Note that environment variables set in 
**Project Settings > Environment Variables** are encrypted and secure and can be used to store
credentials related to signing and deployment.

##A note on code-generating tools
Many iOS app developers use tools that generate substantial amounts of code. In such
cases CircleCI's inference may not correctly detect the Xcode workspace, project, or
scheme. Instead, you can specify these through [environment variables](#environment-variables).


##Constraints on OSX-based builds
During the Beta phase, there are a few features normally available on CircleCI's standard
Linux containers that are not available on OSX vms:

* It is not possible yet to SSH into build containers
* Parallelism is not supported
* While the general `circle.yml` file structure will be honored in OSX-based builds
[configuration options](/docs/configuration) that would normally be specified in the
`machine:language`, `machine:services`, or a few other sections will not work correctly.
See the [customizing your build](#customizing-your-build) section for alternatives.
* All environement variables should be specified in **Project Settings > Environment Variables**
instead of in the `circle.yml` file. (This is a temporary shortcoming that should be resolved soon.)
