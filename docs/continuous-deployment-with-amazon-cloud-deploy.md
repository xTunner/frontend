## Getting Started with AWS Cloud Deploy on CircleCI

### AWS infrastructure
The first step to continuous deployment with Cloud Deploy is setting up your
EC2 instances, tagging them so you can define deployment groups, installing the
Cloud Deploy agent on your hosts and setting up trust-roles so that Cloud
Deploy can communicate with the Cloud Deploy agents.
AWS provide a good [getting started with Cloud Deploy guide][] for this part of
the process.

[gettings started with Cloud Deploy guide]: http://alpha-docs-aws.amazon.com/en_us/console/sds/applications-tutorial


### Cloud Deploy Application
A Cloud Deploy application is a collection of settings about where your
application can be deployed, how many instances can be deployed to at once,
what should be considered a failed deploy, and information on the trust-role to
use to allow Cloud Deploy to interact with your EC2 instances.

**Note**: A Cloud Deploy application does not specify what is to be deployed or what to
do during the deployment.
*What* to deploy is an archive of code/resources stored in S3 called an
`application revision`.
*How* to deploy is specified by the `AppSpec` file located inside the
application revision.

The [AppSpec][] file lives in your repo and tells Cloud Deploy which files from
your application to deploy, where to deploy them, and also allows you specify
lifecyle scripts to be run at different stages during the deployment. You can
use these lifecycle scripts to stop your service before a new version is
deployed, run database migrations, install dependencies etc.

An [application revision][] is a zipfile or tarball containing the code/resources
to be deployed, it's usually created by packaging up your entire repo but can
be a sub-directory of the repo if necessary. The `AppSpec` file must be stored
in the application revision as
`<revision-root>/SimpleDeployService/AppSpecs/default.yml`. Generally speaking
you'll have one application in your repo and so your application revision can
be created by packaging up your whole repo (excluding `.git`).

"Application revisions" are stored in [S3][] and are identified by the
combination of a bucket name, key, eTag, and for versioned buckets, the object's
version.

The most straightforward way to configure a new application is to log on to the
[Cloud Deploy console][] which can guide you through the process of [creating a new
application][].

[AppSpec]: link to doc on appspecs
[application revision]: link to doc on application revisions
[S3]: http://aws.amazon.com/s3/
[Cloud Deploy console]: https://razorbill-preview-console.aws.amazon.com/sds/home
[creating a new application]: https://razorbill-preview-console.aws.amazon.com/sds/home#/applications/new



## Configuring CircleCI

CircleCI will automatically create new application revisions, upload them to
S3, and both trigger and watch deployments when you get a green build.

**Step 1:**
Provide CircleCI with AWS credentials that can be used with Simple Deploy
Service. You should create an [IAM][] user to use solely for builds on
CircleCI, that way you have control over exactly which of your resources can be
accessed by code running as part of your build.

Take note of the Access Key ID and Secret Access Key allocated to your new IAM
user, you'll need these later.

For deploying with Cloud Deploy your IAM user needs to be able to access S3 and
Cloud Deploy at a minimum.

### S3
CircleCI needs to be able to upload application revisions to your S3 bucket.
The following policy snippet allows us to upload to `my-bucket` as long as the
key starts with `my-app`.

**TODO: Validate this policy snippet**

    {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Action": [
            "s3:PutObject"
          ],
          "Resource": [
            # permissions can be scoped down to the common-prefix you use for
            # application revision keys
            "arn:aws:s3:::my-bucket/my-app*",
          ]
        }
      ]
    }


### Cloud Deploy
CircleCI also needs to be able to create application revisions, trigger
deployments and get deployment status. If your application is called `my-app`
the following policy snippet gives us sufficient accesss:

**TODO: Find the correct permissions and test them**

    {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Action": [
            "cloud-deploy:??"
          ],
          "Resource": [
            "arn:aws:cloud-deploy:...?"
          ]
        }
      ]
    }

[IAM]: http://docs.aws.amazon.com/IAM/latest/UserGuide/IAM_Introduction.html


**Step 2:**
Configure CircleCI to use your new IAM user 

Go to your project's **Project Settings > AWS keys** page, enter your IAM
user's Access Key ID and Secret Access Key and hit save.

**TODO: insert images showing where to click**


**Step 3:**
CircleCI needs some additional information to be able to package up your app
and register new revisions:
1. The directory in your repo to package up. This is relative to your repo's
   root, `/` means the repo's root directory, `/app` means the `app` directory
   in your repo's root directory.
2. Where to store it in S3. The bucket name and a pattern to use to generate
   new keys within that bucket. You can use [substitution variables](link to
   key_pattern section of doc) in your key pattern to help generate a unique
   key for each application revision.
3. Which AWS region your application lives in.

If you want to be able to deploy this application from several different
branches (e.g. deploy `development` to your staging instances and `master` to
your production instances) you can configure these project-wide application
settings in the CirclCI UI at **Project Settings > Amazon Cloud Deploy**. The
main benefit is that you will have a simpler [circle.yml][] file.

You can also skip this step and configure everything in your [circle.yml][]


**Step 4:**
Configure your Cloud Deploy deployment using the `cloud-deploy` block in
[circle.yml][]. At a minimum you need to tell CircleCI which deployment group
the selected branch should be deployed to, any additional settings will
override the project-wide configuration in the project settings UI.

E.g.:

    deployment:
      staging:
        branch: development
        cloud-deploy:
          my-app:
            deployment_group: staging-instance-group

Or, if you wanted to override the S3 location for your application revisions
built for your production deployments:

    deployment:
      production:
        branch: master
        cloud-deploy:
          my-app:
            deployment_group: production-instance-group
            s3_location:
              bucket_name: production-bucket
              key_pattern: apps/my-app-master-{SHORT_COMMIT}-{BUILD_NUM}

If you haven't provided project-wide settings you need to provide all the
information for your deployment in your [circle.yml][]:

    deployment:
      staging:
        branch: development
        cloud-deploy:
          my-app:
            revision_root: /
            s3_location:
              bucket_name: staging-bucket
              key_pattern: apps/my-app-{SHORT_COMMIT}-{BUILD_NUM}
            region: us-east-1
            deployment_group: cloud-deploy-test-project-instance-group
            deployment_config: **TODO find out the new **


Breaking this down: there's one entry in the `cloud-deploy` block which is
named with your Cloud Deploy application's name (in this example we're
deploying an application called `my-app`).

The sub-entries of `my-app` tell CircleCI where and how to deploy the `my-app`
application.

* `revision_root` is the directory to package up into an application revision. It
  is relative to your repo and must start with a `/`.
  `/` means the repo root directory.
  The entire contents of `revision_root` will be packaged up into a zipfile and
  uploaded to S3.
* `s3_location` tells CircleCI where to upload application revisions to.
  * `bucket_name` is the name of the bucket that should store your application
    revision bundles.
  * `key_pattern` is used to generate the S3 key. You can use [substitution variables][]
    to generate unique keys for each build.
* `region` is the AWS region your application lives in
* `deployment_group` [**TODO check if this is optional**] is the deployment group to deploy to
* `deployment_config` [optional] names the deployment configuration. It can be
  any of the standard three Cloud Deploy configurations (FOO, BAR, and BAZ) or
  if you want to use a custom configuration you've created you can name it
  here.


#### Key Patterns
Rather than overwriting a single S3 key with each new revision CircleCI can
generate unique keys for application revisions using substitution variables.

The available variables are:
* `{BRANCH}`: the branch being built
* `{COMMIT}`: the full SHA1 of the commit being build
* `{SHORT_COMMIT}`: the first 7 characters of the commit SHA1
* `{BUILD_NUM}`: the build number

For a unique key you'll need to embed at least one of `{COMMIT}`,
`{SHORT_COMMIT}`, or `{BUILD_NUM}` in your key pattern.

If you'd rather use a versioned bucket just use a fixed string for the key
pattern and we'll use the object's versioning info instead.


## Pre- and post-deployment steps
Unlike other deployment options you don't need to specify pre- or
post-deployment steps in your `circle.yml`.

Cloud Deploy provides first class support for your application's lifecycle via
lifecycle scripts. As a result you can consistently start/stop services, run
database migrations, install dependencies etc. across all your instances in a
consistent manner.

[substitution variables]: link to Key Patterns section of doc
[circle.yml]: /docs/configuration#deployment
