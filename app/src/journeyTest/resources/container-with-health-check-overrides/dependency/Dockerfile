FROM alpine:3.10.1

COPY health-check.sh /tools/
HEALTHCHECK --interval=0.01s --retries=1 CMD /tools/health-check.sh

CMD sh -c 'sleep 0.5; touch /tmp/ready; sleep 10000'
