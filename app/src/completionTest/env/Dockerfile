FROM alpine:3.12.3@sha256:3c7497bf0c7af93428242d6176e8f7905f2201d8fc5861f45be7a346b5f23436

RUN apk --no-cache add \
    bash==5.0.17-r0 \
    curl==7.69.1-r3 \
    fish==3.1.2-r0 \
    openjdk8-jre=8.275.01-r0 \
    python3==3.8.5-r0 \
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
