FROM alpine:3.13.2@sha256:a75afd8b57e7f34e4dad8d65e2c7ba2e1975c795ce1ee22fa34f8cf46f96a3be

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
ARG ZSH_COMPLETION_COMMIT_SHA=a575e77fa456b88c77887befc7dc39c51d426789

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/zshrc \
    -o /root/.zshrc

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/complete.zsh \
      -o /usr/local/bin/complete.zsh && \
    chmod +x /usr/local/bin/complete.zsh

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/completions/_batect \
    -o /usr/share/zsh/site-functions/_batect
