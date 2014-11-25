<!--

title: Setting up parallelism
last_updated: Nov 21, 2014

-->

This two-minute video shows you everything you need to do to turn parallelism on for
your project. (This video assumes you already have a green build without parallelism.
Check out our [getting started guide](/docs/getting-started) if you haven't setup your
project yet.)

<iframe src='//www.youtube.com/embed/rcUNKT5xd4Q?rel=0' width='600' height='400' frameborder='0' allowfullscreen></iframe>

In summary, just go to the project settings page by clicking on the gear icon in the top-right, then click "Parallelism". Then simply choose the number of parallel containers you want to use on each build.

CircleCI can automatically split your tests across multiple containers for many test
runners, including RSpec, Cucumber, minitest, Django, Nose, and more. However, if our
inferred parallel test commands don't work, or if you want to do custom test splitting,
see [our doc on manually setting up parallelism](/docs/parallel-manual-setup).
