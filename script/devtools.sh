#!/bin/bash
set -ex

if [ -a "/opt/homebrew-cask/Caskroom/google-chrome-canary/latest" ]; then
  CHROME_PATH="/opt/homebrew-cask/Caskroom/google-chrome-canary/latest"
else
  CHROME_PATH="/Applications"
fi

$CHROME_PATH/Google\ Chrome\ Canary.app/Contents/MacOS/Google\ Chrome\ Canary \
  --remote-debugging-port=9222 \
  --no-first-run \
  https://prod.circlehost:4443 &

lein with-profile +devtools repl
