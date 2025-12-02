#!/bin/sh

# Replace BACKEND_URL placeholder in nginx config with actual environment variable value
# If BACKEND_URL is not set, use default value
if [ -z "$BACKEND_URL" ]; then
    BACKEND_URL="http://backend:8080"
fi

# Replace the placeholder in nginx.conf
sed -i "s|\${BACKEND_URL}|$BACKEND_URL|g" /etc/nginx/conf.d/default.conf

# Start nginx
exec nginx -g 'daemon off;'