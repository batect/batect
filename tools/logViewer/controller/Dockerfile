FROM alpine:3.12.0

RUN apk --no-cache add \
    python3 \
    py3-requests

RUN mkdir -p /tools
COPY run.py /tools

CMD ["/tools/run.py"]
