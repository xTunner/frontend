<!--

title: Frequently Asked Questions
short_title: FAQ
last_updated: February 23, 2015

-->

## Authentication

### I can’t give CircleCI the access to all my private repositories.
### What do I do?
GitHub has only recently added the fine-grained permissions options, and
we still working on supporting them.

In the meantime, the suggested workaround is to create an additional
user on GitHub with a limited set of permissions and use that account to
perform the builds on CircleCI.

## Billing & Plans

### Can I build more than one project if I only have one container?
Absolutely. In this case the builds will run one at a time, one after
another.

### How do I stop CircleCI from building a project?
Have everyone who follows the project on CircleCI unfollow it, and we
will automatically stop building it. Another way is to disable the
Github hook for the project you want to stop building.

## Integrations

### Do you support BitBucket or GitLab?
No, currently only authentication with GitHub is supported.

### Can I send HipChat / Slack / IRC notifications for specific branches only?
We don’t currently offer this kind of selective notifications, but we
hope to ship this feature very soon. Keep an eye on our
[changelog](https://circleci.com/changelog) to be notified as soon as
this feature is available.

## Dependencies
### How do I use postgres 9.4?
PostgreSQL 9.4 is currently not shipped with our build containers by
default, but you can install it manually following the steps described
[in this gist](https://gist.github.com/alex88/f5ddd968256bae2c00ec).

### How do I use mysql 5.6?
MySQL 5.6 is not in our build containers yet, but you can install it
manually as well. Just follow
[these](https://circleci.com/docs/installing-custom-software#installing-via-circle-yml)
steps.

### How do I use Docker 1.5
We have a set of custom patches for Docker that need to be applied to
1.5 to make sure it runs securely in our environment. We are not there
yet, but we try hard to make it available as soon as possible.
