FROM nginx:1.17.9

COPY health-check.sh /tools/

HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh

CMD ["sh", "-c", "echo 'This is some output from the HTTP server' && nginx -g 'daemon off;'"]
