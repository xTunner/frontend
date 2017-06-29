#!/bin/bash

set -ex

if [[ "$1" = 'start' ]]
then
    # Generate Cert if none is present
    if [[ ! -s /frontend/nginx/etc/nginx/ssl/star.circlehost.key ]]
    then
        mkdir -p /frontend/nginx/etc/nginx/ssl
        openssl req -batch -new \
            -x509 -newkey rsa:2048 -sha256 -nodes -days 365 \
            -subj '/C=US/ST=California/L=San Francisco/O=CircleCI/CN=*.circlehost' \
            -keyout /etc/ssl/private/star.circlehost.key \
            -out /frontend/nginx/etc/nginx/ssl/star.circlehost.crt
    fi

    if [[ ! -s /frontend/nginx/etc/nginx/ssl/star.circlehost.pem ]]
    then
        cat /frontend/nginx/etc/nginx/ssl/star.circlehost.key \
            /frontend/nginx/etc/nginx/ssl/star.circlehost.crt \
            > /frontend/nginx/etc/nginx/ssl/star.circlehost.pem
    fi

    (cat <<'EOF'
global
    daemon
    maxconn 4096

listen http
  bind 0.0.0.0:13000
  bind 0.0.0.0:14443 ssl crt /frontend/nginx/etc/nginx/ssl/star.circlehost.pem
  mode tcp
  server master 127.0.0.1:3000
  timeout client 3600s
  timeout connect 3600s
  timeout server 3600s

EOF
) > /etc/haproxy/haproxy.cfg

    # Generate HAProxy
    haproxy -f /etc/haproxy/haproxy.cfg

    lein repl :headless &
    lein run
fi

exec "$@"
