FROM nginx:1.13.0

COPY health-check.sh /tools

HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh
