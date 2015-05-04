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

### Clearing the cache

The _Rebuild without cache_ button in the UI will disable the cache for
a single build so that you could debug any problems caused by your
dependencies.

If such rebuild successfully passes the dependency phase, it will save
a new cache which will be used by future builds.

Normally you will not have to clear the cache permanently, but if you
feel that’s what you need, you can just remove the necessary parts of
the cache anywhere in your `circle.yml`’s `dependencies` section,
before the cache is saved:

```
dependencies:
  post:
    - rm -r ~/.gradle
```
