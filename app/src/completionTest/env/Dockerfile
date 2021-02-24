FROM alpine:3.13.1@sha256:08d6ca16c60fe7490c03d10dc339d9fd8ea67c6466dea8d558526b1330a85930

RUN apk --no-cache add \
    bash==5.1.0-r0 \
    curl==7.74.0-r0 \
    fish==3.1.2-r4 \
    openjdk8-jre=8.275.01-r0 \
    python3==3.8.7-r1 \
    zsh==5.8-r1

# fish completion
RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/fish-completion/45881e3d7b55b4c648196284194de8e3536f0afc/completions/batect.fish \
    -o /usr/share/fish/vendor_completions.d/batect.fish

# zsh completion
ARG ZSH_COMPLETION_COMMIT_SHA=3ae24fb9f0f4079a12fc8f4a7b50f17f1706ff40

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/zshrc \
    -o /root/.zshrc

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/complete.zsh \
      -o /usr/local/bin/complete.zsh && \
    chmod +x /usr/local/bin/complete.zsh

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/completions/_batect \
    -o /usr/share/zsh/site-functions/_batect
