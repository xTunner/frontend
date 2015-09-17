#!/bin/bash

set -ex

if [[ "$1" = 'start' ]]
then
    # Generate HAProxy
    haproxy -f /etc/haproxy/haproxy.cfg

    lein figwheel dev &
    lein run
fi

exec "$@"
