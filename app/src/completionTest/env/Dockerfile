FROM alpine:3.13.4@sha256:ec14c7992a97fc11425907e908340c6c3d6ff602f5f13d899e6b7027c9b4133a

RUN apk --no-cache add \
    bash==5.1.0-r0 \
    curl==7.76.1-r0 \
    fish==3.1.2-r4 \
    openjdk11-jre=11.0.9_p11-r1 \
    python3==3.8.8-r0 \
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
