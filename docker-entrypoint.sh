#!/bin/bash

set -ex

if [[ "$1" = 'start' ]]
then
    # Generate Cert if none is present
    if [[ ! -s /etc/ssl/private/star.circlehost.key ]]
    then
        mkdir -p /etc/ssl/private
        openssl req -batch -new \
            -x509 -newkey rsa:2048 -sha256 -nodes -days 365 \
            -subj '/C=US/ST=California/L=San Francisco/O=CircleCI/CN=*.circlehost' \
            -keyout /etc/ssl/private/star.circlehost.key \
            -out /etc/ssl/private/star.circlehost.crt
    fi

    if [[ ! -s /etc/ssl/private/star.circlehost.pem ]]
    then
        cat /etc/ssl/private/star.circlehost.key \
            /etc/ssl/private/star.circlehost.crt \
            > /etc/ssl/private/star.circlehost.pem
    fi

    (cat <<'EOF'
global
    daemon
    maxconn 4096

listen http
  bind 0.0.0.0:13000
  bind 0.0.0.0:14443 ssl crt /etc/ssl/private/star.circlehost.pem
  mode tcp
  server master 127.0.0.1:3000
  timeout client 3600s
  timeout connect 3600s
  timeout server 3600s

listen fig-repl 0.0.0.0:17888
  mode tcp
  server master 127.0.0.1:7888
  timeout client 3600s
  timeout connect 3600s
  timeout server 3600s

listen dev-repl 0.0.0.0:18230
  mode tcp
  server master 127.0.0.1:8230
  timeout client 3600s
  timeout connect 3600s
  timeout server 3600s

listen figwheel
  bind 0.0.0.0:13449
  bind 0.0.0.0:14444 ssl crt /etc/ssl/private/star.circlehost.pem
  mode tcp
  server master 127.0.0.1:3449
  timeout client 3600s
  timeout connect 3600s
  timeout server 3600s

EOF
) > /etc/haproxy/haproxy.cfg

    # Generate HAProxy
    haproxy -f /etc/haproxy/haproxy.cfg

    lein repl :headless &
    lein run -m frontend.core-dev
fi

exec "$@"
