<!--

title: Collecting test metadata
last_updated: Feb 17, 2015

-->

CircleCI can collect test metadata from JUnit XML files and Cucumber JSON files.
We'll use the test metadata to give you better insight into your build. For our
inferred steps that use parallelism, we'll use the timing information to get you
better test splits and finish your builds faster.

## Automatic test metadata collection

If you're using our inferred test steps for RSpec, Cucumber, or Minitest for Ruby or nosetests for Python then we'll
automatically collect test metadata,
though for RSpec and Minitest you'll need to add the necessary formatter gems to your Gemfile:

For RSpec:

```
gem 'rspec_junit_formatter', :git => 'git@github.com:circleci/rspec_junit_formatter.git'
```

For Minitest:

```
gem 'minitest-ci', :git => 'git@github.com:circleci/minitest-ci.git'
```

## Metadata collection in custom test steps

If you have a custom test step that produces JUnit XML output - most test runners support this in some form - you can write the XML
files to the `$CIRCLE_TEST_REPORTS` directory.  We'll automatically store the files in your
[build artifacts](/docs/build-artifacts) and parse the XML.

You can tell us the type of test by putting the files in a subdirectory of `$CIRCLE_TEST_REPORTS`.
For example, if you have RSpec tests, you would write your XML files to `$CIRCLE_TEST_REPORTS/rspec`.

### Cucumber

For custom Cucumber steps, you should generate a file using the JSON formatter that ends
with `.cucumber` and write it to the `$CIRCLE_TEST_REPORTS/cucumber` directory.  Your [circle.yml](/docs/configuration) might be:

```
test:
  override:
    - mkdir -p $CIRCLE_TEST_REPORTS/cucumber
    - bundle exec cucumber --format json --out $CIRCLE_TEST_REPORTS/cucumber/tests.cucumber
```

### Java JUnit results with Maven Surefire Plugin

If you are building a [Gradle](https://gradle.org/) or
[Maven](http://maven.apache.org/) based project, you are more than likely using
the [Maven Surefire plugin](http://maven.apache.org/surefire/maven-surefire-plugin/)
to generate test reports in XML format. CircleCI makes it easy to collect these
reports. You just need to add the followng to the `circle.yml` file in your
project.

```
test:
  post:
    - mkdir -p $CIRCLE_TEST_REPORTS/junit/
    - find . -type f -regex ".*/target/surefire-reports/.*xml" -exec cp {} $CIRCLE_TEST_REPORTS/junit/ \;
```

## API

You can access test metadata for a build from the [API](/docs/api#test-metadata).
