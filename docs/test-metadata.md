<!--

title: Collecting test metadata
last_updated: Nov 10, 2014

-->

CircleCI can collect test metadata from JUnit xml files and Cucumber json files.
We'll use the test metadata to give you better insight into your build. For our
inferred steps that use parallelism, we'll use the timing information to get you
better test splits and finish your builds faster.

## Automatic test metatdata collection

If you're using our inferred test steps for RSpec, Cucumber, or Minitest, then we'll
automatically collect test metadata.

For RSpec, you'll have to add our junit formatter gem to your Gemfile:

```
gem 'rspec_junit_formatter', :git => 'git@github.com:circleci/rspec_junit_formatter.git'
```

For Minitest, you'll have to add the `minitest-ci` gem.

## Custom steps

If you have a custom test step that produces JUnit xml output, you can put the xml
files into the `$CIRCLE_TEST_REPORTS` directory. We'll automatically store the files in your
[build artifacts](/docs/build-artifacts) and parse the xml.

You can tell us the type of test by putting the files in a subdirectory of `$CIRCLE_TEST_REPORTS`.
For example, if you have RSpec tests, you would move your xml files to `$CIRCLE_TEST_REPORTS/rspec`.

### Cucumber

For custom Cucumber steps, you should generate a file using the json formatter that ends
with `.cucumber` and move it to the `$CIRCLE_TEST_REPORTS/cucumber` directory. Your [circle.yml](/docs/configuration)
might be:

```
test:
  override:
    - mkdir -p $CIRCLE_TEST_REPORTS/cucumber
    - bundle exec cucumber --format json --out $CIRCLE_TEST_REPORTS/cucumber/tests.cucumber
```

## API

You can access test metadata for a build from the [API](/docs/api#test-metadata).
