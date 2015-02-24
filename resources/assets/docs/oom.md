<!--

title: OOM killer ran
last_updated: Feb 2, 2013

-->

Your build contains a message that says the Linux Out-of-Memory (OOM)
killer ran.

The reason for this is your builds run in a VM with 4GB of
available RAM. If you go over that limit, Linux kills a process,
somewhat arbitrarily.

Builds which run out of memory in this way result in a file being
generated called `memory-usage.txt`, which you will find in the artifacts
tab on the build page. This contains information that should help you
debug your build.

If your tests actually need more than 4GB of RAM, please
[contact us](mailto:sayhi@circleci.com).

## Setting memory limits for the JVM

We have seen a couple of cases where the OOM would kill the JVM process
while building an Android project. If that is the case for you, you can
limit the JVM’s usage of memory by declaring the limits in the
`JAVA_OPTS` environment variable, like this:

```
  set JAVA_OPTS="-Xms256m -Xmx512m"
```

Note that you might want to use larger numbers depending on the other
processes running during your build. Check the contents of your
`memory-usage.txt` for the memory usage of the rest of the processes and
adjust the parameters adequately.

## Debugging

Use the [SSH button](/docs/ssh-build)
to ssh into a running build and run `top`.

Hit `Shift + M` to sort by memory usage and watch what process is using
the most memory while your tests run.

The number to pay attention to is the RES (short for resident) column.
This tracks the actual RAM used by a process. Note that the 4GB limit
applies to the sum of all processes running in your container, not
just a single process.

One thing to keep in mind is that the %MEM you see in top is the
percentage of the entire machine, not just the container that your builds
are running in. The OOM killer typically runs when a process uses up
2-3% of total memory.
