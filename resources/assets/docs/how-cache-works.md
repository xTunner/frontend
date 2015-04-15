<!--

title: How cache works
last_updated: Aug 15, 2015

-->

The cache takes care of saving the state of your dependencies between
builds, therefore making the builds run faster.

CircleCI will cache all the directories you specify in the
`dependencies: cache_directories` section of your `circle.yml`, plus
the directories we determine via inference.

### Per-branch cache

Each branch of your project will have a separate cache. If it is the
very first build for a branch, the cache from the default branch on
GitHub (normally `master`) will be used. If there is no cache for
`master`, the cache from other branches will be used.

### Caching via inference

We will automatically cache the dependencies for you if you are using
Ruby (Rubygems), Python (pip), iOS (Pods), PHP (composer), Java
(gradle), Scala (sbt) packages.

### Caching for apt

We don’t support caching for packages you install via `apt-get`. If you
want to cache those, the best way to do it would be to download the
`deb` packages to a directory specified in `cache_directories` and then
install them using `dpkg`.

### Clearing the cache

Normally you will not have to clear the cache, but if you feel that’s
what you need, you can use [this API
endpoint](https://circleci.com/docs/api#clear-cache) for that.
