#!/bin/bash

# this is the wrapper command used by git commands.

ssh-add -D &> /dev/null  ## ssh -i doesn't play well with agents.

if [ ! -z "$GIT_SSH_KEY" ]; then
    chmod 600 "$GIT_SSH_KEY"
    exec ssh -i "$GIT_SSH_KEY" "$@"
else
    exec ssh "$@"
fi