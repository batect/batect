FROM alpine:3.12.3@sha256:074d3636ebda6dd446d0d00304c4454f468237fdacf08fb0eeac90bdbfa1bac7

RUN apk --no-cache add \
    bash==5.0.17-r0 \
    curl==7.69.1-r3 \
    fish==3.1.2-r0 \
    openjdk8-jre=8.252.09-r0 \
    python3==3.8.5-r0

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/fish-completion/45881e3d7b55b4c648196284194de8e3536f0afc/completions/batect.fish \
    -o /usr/share/fish/vendor_completions.d/batect.fish
