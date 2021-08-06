FROM alpine:3.14.1

ARG CACHE_BUSTING_ID
RUN echo "This is build $CACHE_BUSTING_ID"

ADD http://httpbin.org/robots.txt /test.txt
