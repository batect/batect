FROM nginx:1.13.0

COPY index.html /usr/share/nginx/html
COPY health-check.sh /tools/

HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh
