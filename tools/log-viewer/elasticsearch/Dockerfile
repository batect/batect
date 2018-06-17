FROM docker.elastic.co/elasticsearch/elasticsearch:6.2.4

RUN mkdir -p /tools
COPY health-check.sh /tools
HEALTHCHECK --interval=1s --retries=20 CMD /tools/health-check.sh
