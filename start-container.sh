#!/bin/sh
set -eu

envsubst '${PORT} ${SERVER_PORT} ${SOCKET_PORT}' \
    < /app/nginx.conf.template \
    > /tmp/nginx.conf

exec /usr/bin/supervisord -n -c /app/supervisord.conf
