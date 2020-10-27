FROM alpine:3.12.1@sha256:d7342993700f8cd7aba8496c2d0e57be0666e80b4c441925fc6f9361fa81d10e

RUN apk --no-cache add \
    bash==5.0.17-r0 \
    curl==7.69.1-r1 \
    fish==3.1.2-r0 \
    openjdk8-jre=8.252.09-r0 \
    python3==3.8.5-r0

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/fish-completion/042ec7a8a8fe11ab8652676b39d5fc9bad956577/completions/batect.fish \
    -o /usr/share/fish/vendor_completions.d/batect.fish
