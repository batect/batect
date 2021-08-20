FROM ubuntu:20.04@sha256:1e48201ccc2ab83afc435394b3bf70af0fa0055215c1e26a5da9b50a1ae367c9

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates=20210119~20.04.1 \
    curl=7.68.0-1ubuntu2.6 \
    fish=3.1.0-1.2 \
    openjdk-11-jre-headless=16.0.1+9-1~20.04 \
    python3=3.8.2-0ubuntu2 \
    zsh=5.8-3ubuntu1 \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# fish completion
RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-fish-completion/45881e3d7b55b4c648196284194de8e3536f0afc/completions/batect.fish \
    -o /usr/share/fish/vendor_completions.d/batect.fish

# zsh completion
ARG ZSH_COMPLETION_COMMIT_SHA=c8745e1f5d56b9dd0b78fca4f8c14f8dd7b87a1e

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/zshrc \
    -o /root/.zshrc

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/complete.zsh \
      -o /usr/local/bin/complete.zsh && \
    chmod +x /usr/local/bin/complete.zsh

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/_batect \
    -o /usr/share/zsh/vendor-completions/_batect
