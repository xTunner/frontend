#!/bin/bash

## simple little script to daemonize the server. Don't call this directly, use rake. Expects that JRUBY_OPTS is already set.

PID_PATH=trinidad.pid
if [ -e $PID_PATH ]
then
    echo "trinidad.pid already exists, aborting";
    exit 1;
fi

trinidad < /dev/null &> trinidad.log &
PID=$!
echo "pid= $!"
echo $PID > trinidad.pid
echo "done"
disown %1
