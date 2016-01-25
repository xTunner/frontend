#!/bin/bash

/Applications/Google\ Chrome\ Canary.app/Contents/MacOS/Google\ Chrome\ Canary \
  --remote-debugging-port=9222 \
  --no-first-run \
  https://prod.circlehost:4443 &

lein with-profile devtools repl
