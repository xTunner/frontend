#!/bin/bash

set -ex

# Only deploy for private repository builds.
if [[ $CIRCLE_PROJECT_REPO_NAME != 'frontend-private' ]]; then
  exit 0
fi

# Git Configuration
export GIT_SSH="$PWD/script/git-ssh-wrap.sh"

# Distribute built public files and publish the sha1 of this branch.
dist_tgz=$DEPLOY_S3_BUCKET/dist/$CIRCLE_SHA1.tgz
tar -cz resources/public/ | aws put --http $dist_tgz
echo $CIRCLE_SHA1 | aws put --http $DEPLOY_S3_BUCKET/branch/$CIRCLE_BRANCH

# Check for matching branch name on backend or create one from production branch.
export KEYPATH="$HOME/.ssh/id_frontend-private"
backend_repo=git@github.com:circleci/circle
backend_heads=$(git ls-remote --heads $backend_repo)
if ! echo $backend_heads | grep "refs/heads/$CIRCLE_BRANCH\b" ; then
  backend_dir=$HOME/checkouts/circle
  git clone $backend_repo $backend_dir
  git -C $backend_dir branch $CIRCLE_BRANCH origin/production
  git -C $backend_dir push origin $CIRCLE_BRANCH:$CIRCLE_BRANCH
fi

# Keep open source master branch up to date.
export KEYPATH="$HOME/.ssh/id_frontend"
public_repo=git@github.com:circleci/frontend
if [[ $CIRCLE_BRNACH = master ]]; then
  git push $public_repo
fi

# Trigger a backend build of this sha1.
circle_api=https://circleci.com/api/v1
tree_url=$circle_api/project/circleci/circle/tree/$CIRCLE_BRANCH
http_status=$(curl -o /dev/null \
                   --silent \
                   --write-out '%{http_code}\n' \
                   --header "Content-Type: application/json" \
                   --data "{\"build_parameters\": {\"FRONTEND_DEPLOY_SHA1\": \"$CIRCLE_SHA1\"}}" \
                   --request POST \
                   -L $tree_url?circle-token=$BACKEND_CIRCLE_TOKEN)
[[ 201 = $http_status ]]
