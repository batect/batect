FROM alpine:3.11.6

COPY health-check.sh /tools/
HEALTHCHECK --interval=0.01s --retries=1 CMD exit 0

CMD sh -c 'sleep 0.5; touch /tmp/ready; sleep 10000'
