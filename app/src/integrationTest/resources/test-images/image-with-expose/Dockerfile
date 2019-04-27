# This is based (loosely) on https://github.com/nginxinc/docker-nginx/blob/master/mainline/stretch/Dockerfile.

FROM alpine:3.7

RUN apk --no-cache add nginx

# Forward request and error logs to stdout / stderr
RUN ln -sf /dev/stdout /var/log/nginx/access.log \
    && ln -sf /dev/stderr /var/log/nginx/error.log

RUN mkdir -p /run/nginx

RUN sed -i 's/return 404/return 200/' /etc/nginx/conf.d/default.conf

STOPSIGNAL SIGTERM

CMD ["nginx", "-g", "daemon off;"]

EXPOSE 80
