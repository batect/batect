FROM nginx:1.13.0

RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*

COPY index.html /usr/share/nginx/html
COPY health-check.sh /tools/

HEALTHCHECK --interval=2s --retries=1 CMD /tools/health-check.sh
