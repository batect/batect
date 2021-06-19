FROM alpine:3.14.0@sha256:234cb88d3020898631af0ccbbcca9a66ae7306ecd30c9720690858c1b007d2a0

RUN apk --no-cache add \
    bash==5.1.4-r0 \
    curl==7.77.0-r1 \
    fish==3.1.2-r4 \
    openjdk11-jre=11.0.11_p9-r0 \
    python3==3.8.10-r0 \
    zsh==5.8-r1

# fish completion
RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-fish-completion/45881e3d7b55b4c648196284194de8e3536f0afc/completions/batect.fish \
    -o /usr/share/fish/vendor_completions.d/batect.fish

# zsh completion
ARG ZSH_COMPLETION_COMMIT_SHA=a575e77fa456b88c77887befc7dc39c51d426789

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/zshrc \
    -o /root/.zshrc

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/complete.zsh \
      -o /usr/local/bin/complete.zsh && \
    chmod +x /usr/local/bin/complete.zsh

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/completions/_batect \
    -o /usr/share/zsh/site-functions/_batect
