<!--

title: Continuous Integration and Continuous Deployment with Scala
short_title: Scala
last_updated: Mar 3, 2015

-->

Circle supports building Scala applications with `sbt`. Before each
build we look at your repository and infer commands to run, so most
setups should work automatically.

If you'd like something specific that's not being inferred,
[you can say so](/docs/configuration) with
[a configuration file](/docs/config-sample)
checked into the root of your repository.

### Version

Circle has
[several versions of Scala](/docs/environment#scala)
available. We use `{{ versions.scala }}`
as the default; if you'd like a particular version, you
can specify it in your `circle.yml`:

```
machine:
  scala:
    version: 0.13.0
```

### Dependencies & Tests

Circle can [cache directories](/docs/configuration#cache-directories)
in between builds to avoid unnecessary work.

### Artifacts

Circle supports saving and uploading arbitrary
[build artifacts](/docs/build-artifacts).

If you'd like to automatically generate documentation with Haddock,
you can put something like this in your `circle.yml`:

```
test:
  post:
    - something # TODO
```

### Troubleshooting

Our [Haskell troubleshooting](/docs/troubleshooting-haskell)
documentation has information about the following issues:

*   [Unexpected Timeouts During `cabal test`](/docs/cabal-test-timeout)

If you have any further trouble, please [contact us](mailto:sayhi@circleci.com).
We'll be happy to help!
