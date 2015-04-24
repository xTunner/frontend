<!--

title: Building Pull Requests from forks
short_title: Fork PR Builds
last_updated: April 2, 2015

-->

## Quick Start

Suppose your repository is https://github.com/yourorg/yourproject, and someone creates a fork at
otherdev/yourproject

* If yourorg/yourproject is a public repository, we will run builds on for pull requests 
  from otherdev/yourproject under yourorg/yourproject on CircleCI, with some restrictions
  outlined below.

* UNLESS otherdev/yourproject is explicitly configured to build on CircleCI in its own right.

* If yourorg/yourproject is private, we *will not* automatically run builds against pull requests 
  from the fork. You can explicitly allow fork pr builds using the experimental feature setting on 
  yourorg/yourproject at Project settings > Experimental Settings > Project, but it will expose
  sensitive information to the fork developers.

## Details

### Security Implications of running builds for pull requests from forks

When someone submits a pull request against your project you want to see if the 
tests pass before deciding whether to accept their code or not. In the case of a PR from a fork
it would be great if the forker didn't have to know or care that the forkee is using CircleCI.

The mechanism that GitHub uses to implement the pull request machinery is specially named branches 
in the parent repository. When a pull request is created, GitHub creates a new specially named
branch in the "requestee" repository.

Easy enough, you think. CircleCI should just run builds for the special PR branches in the parent 
repository, right? However, project settings on CircleCI often
contain sensitive information. In general, any configuration which is made available to a build 
is accessible to anyone who can push code which triggers the build (because that person can 
just include commands in circle.yml to echo the data to the terminal, and see it on the build page, 
for instance.) So in this special case of running builds for fork PRs, we have to be careful.

There are 4 kinds of configuration data that we would normally use in a build which we suppress
for builds triggered by pull requests from forks:

1. **Environment variables configured via the web UI**
   (configured in Project settings > Tweaks > Environment variables)

   Non-sensitive environment variables for your project can (arguably should) be set 
   in circle.yml; values configured via the web UI are stored encrypted at rest and
   injected into the build environment during regular builds.

   If the same key is set in both, the web UI value overrides the circle.yml one.

2. **Checkout keys**
   (configured in Project settings > Permissions > Checkout SSH keys)

   In normal circumstances, we use either a per project deploy key or a GitHub user key to check 
   out the code during a build. Deploy keys are read/write keys for the project, while a user
   key can be used to act as that user on GitHub.

   For safe fork PR builds, we use a user key associated with a machine GitHub user that 
   CircleCI controls, so if they key is leaked due to a malicious PR it has no impact
   on your project.

3. **SSH keys** 
   (configured in Project settings > Permissions > SSH Permissions)

   These are passphraseless private keys which you can add to CircleCI to access arbitrary 
   hosts during a build. They are not made available to fork PR builds.

4. **AWS permissions**
   (configured in Project settings > Permissions > AWS Permissions)

   This configuration gets stored in files in ~/.aws/ and would let anyone ... 

5. **Deployment credentials**

   Currently we offer streamlined configuration to facilitate deployment to heroku and 
   CodeDeploy. Any credentials you configure here will not be made available to fork PR builds.

#### CircleCI will only run builds of pull requests from forks for public GitHub repositories by 
default. 

We do this because of the **Checkout keys** issue above; we can't use a generic user key to 
check out your code for a private repository.


### Unsafe fork PR builds

In the event that you *want* these four categories of configuration to be made available, or you need
to run fork PR builds for private repositories, what can you do?

There is a per-project flag (in Project settings > Experimental Settings > Project fork pull requests)
which will cause us to run builds of all fork pull requests without suppressing any of the sensitive 
information listed above.
