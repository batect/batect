FROM alpine:3.14.0

RUN apk add --no-cache tree

ARG CACHE_BUSTING_ID
RUN echo "This is build $CACHE_BUSTING_ID"

COPY . /app/
