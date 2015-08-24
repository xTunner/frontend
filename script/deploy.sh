#!/bin/bash

set -ex

# Only deploy for private repository builds.
if [[ $CIRCLE_PROJECT_REPONAME != 'frontend-private' ]]; then
  exit 0
fi

# Git Configuration
export GIT_SSH="$PWD/script/git-ssh-wrap.sh"

# Distribute built public files and publish the sha1 of this branch.
tar -cz resources/public/ | aws s3 cp - s3://$DEPLOY_S3_BUCKET/dist/$CIRCLE_SHA1.tgz
echo $CIRCLE_SHA1 | aws s3 cp - s3://$DEPLOY_S3_BUCKET/branch/$CIRCLE_BRANCH

# Keep open source master branch up to date.
export KEYPATH="$HOME/.ssh/id_frontend"
public_repo=git@github.com:circleci/frontend
if [[ $CIRCLE_BRANCH = master ]]; then
  git push $public_repo
fi

# Trigger a build on circleci/circle with latest assets

# Check for matching branch name on backend or create one from production branch.
export KEYPATH="$HOME/.ssh/id_frontend-private"
backend_repo=git@github.com:circleci/circle
heads_file=$(mktemp)
git ls-remote --heads $backend_repo > $heads_file

# backend_branch may be overridden by the next block
backend_branch=$CIRCLE_BRANCH

if ! grep -e "refs/heads/${CIRCLE_BRANCH}$" $heads_file ; then
  # Create a new branch if one doesn't exist
  backend_dir=$HOME/checkouts/circle
  git clone $backend_repo $backend_dir
  # the z is so that it gets pushed to the end of the branch list
  backend_branch="zfe/$CIRCLE_BRANCH"
  git -C $backend_dir branch $backend_branch origin/production
  git -C $backend_dir push origin $backend_branch:$backend_branch
else
  # Trigger a backend build of this sha1.
  circle_api=https://circleci.com/api/v1
  tree_url=$circle_api/project/circleci/circle/tree/$backend_branch
  http_status=$(curl -o /dev/null \
                     --silent \
                     --write-out '%{http_code}\n' \
                     --header "Content-Type: application/json" \
                     --data "{\"build_parameters\": {\"FRONTEND_DEPLOY_SHA1\": \"$CIRCLE_SHA1\"}}" \
                     --request POST \
                     -L $tree_url?circle-token=$BACKEND_CIRCLE_TOKEN)
  [[ 201 = $http_status ]]
fi
