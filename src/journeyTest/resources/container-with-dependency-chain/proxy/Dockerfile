FROM nginx:1.13.0

COPY default.conf /etc/nginx/conf.d/default.conf
COPY health-check.sh /tools/

HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh
