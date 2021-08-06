FROM alpine:3.14.1

COPY health-check.sh /tools/

HEALTHCHECK --interval=2s --timeout=15s --retries=1 CMD /tools/health-check.sh
