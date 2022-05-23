FROM alpine:3.16.0

COPY health-check.sh /tools/

HEALTHCHECK --interval=2s --timeout=15s --retries=1 CMD /tools/health-check.sh
