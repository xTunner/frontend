#!/bin/bash

set -ex

git config user.email "ian@iankduncan.com"
git config user.name "Ian Duncan"

git checkout ship-v2
git pull --rebase
git merge $CIRCLE_SHA1 --no-edit
git push origin ship-v2
