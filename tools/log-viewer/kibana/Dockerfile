FROM docker.elastic.co/kibana/kibana:6.6.2

RUN curl --fail --location https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 -o /usr/share/kibana/bin/jq
RUN chmod +x /usr/share/kibana/bin/jq

COPY health-check.sh /usr/share/kibana/bin
HEALTHCHECK --interval=1s --retries=30 CMD /usr/share/kibana/bin/health-check.sh

# For some reason, kibana doesn't respond to SIGTERM, so we have to use SIGKILL.
STOPSIGNAL 9
