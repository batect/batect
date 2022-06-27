FROM ubuntu:22.04@sha256:06b5d30fabc1fc574f2ecab87375692299d45f8f190d9b71f512deb494114e1f

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates=20211016 \
    curl=7.81.0-1ubuntu1.3 \
    fish=3.3.1+ds-3 \
    openjdk-11-jre-headless=11.0.14.1+1-0ubuntu1 \
    python3=3.10.4-0ubuntu2 \
    python3-pip=22.0.2+dfsg-1 \
    zsh=5.8.1-1 \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# hadolint ignore=DL3003
RUN curl --show-error --retry 3 --retry-connrefused https://ftp.gnu.org/gnu/bash/bash-3.2.57.tar.gz --output /tmp/bash-3.2.57.tar.gz && \
    cd /tmp && tar xzf /tmp/bash-3.2.57.tar.gz && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        bison=2:3.8.2+dfsg-1build1 \
        gcc=4:11.2.0-1ubuntu1 \
        make=4.3-4.1build1 \
        libc6-dev=2.35-0ubuntu3 \
    && \
    cd /tmp/bash-3.2.57 && \
    ./configure --prefix=/shells/bash-3.2 && \
    make && \
    make install && \
    rm -rf /tmp/bash-3.2.57 /tmp/bash-3.2.57.tar.gz && \
    apt-get purge -y bison gcc make libc6-dev && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# hadolint ignore=DL3003
RUN curl --location --show-error --retry 3 --retry-connrefused https://github.com/scop/bash-completion/archive/refs/tags/1.3.tar.gz --output /tmp/bash-completion-1.3.tar.gz && \
    cd /tmp && tar xzf /tmp/bash-completion-1.3.tar.gz && \
    cp /tmp/bash-completion-1.3/bash_completion /etc/bash_completion && \
    rm -rf /tmp/bash-completion-1.3 /tmp/bash-completion-1.3.tar.gz \

ENV PATH=/shells/bash-3.2/bin:$PATH

# Fish completion
RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-fish-completion/45881e3d7b55b4c648196284194de8e3536f0afc/completions/batect.fish \
    -o /usr/share/fish/vendor_completions.d/batect.fish

# Zsh completion
ARG ZSH_COMPLETION_COMMIT_SHA=c5bad435e38efe49f6f27953548a94c60f01f1f0

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/.zshrc \
    -o /root/.zshrc

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/complete.py \
      -o /usr/local/bin/complete.zsh && \
    chmod +x /usr/local/bin/complete.zsh

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/_batect \
    -o /usr/share/zsh/vendor-completions/_batect

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-zsh-completion/$ZSH_COMPLETION_COMMIT_SHA/.batect/test-env/requirements.txt | \
    pip3 install --no-cache-dir -r /dev/stdin

# Bash completion
ARG BASH_COMPLETION_COMMIT_SHA=a23340c25d24a29bc3e9e1e2620ddb3a5ee258c5

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-bash-completion/$BASH_COMPLETION_COMMIT_SHA/.batect/test-env/bashrc \
    -o /root/.bashrc

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-bash-completion/$BASH_COMPLETION_COMMIT_SHA/.batect/test-env/bash_profile \
    -o /root/.bash_profile

RUN curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-bash-completion/$BASH_COMPLETION_COMMIT_SHA/.batect/test-env/test_complete.bash \
      -o /usr/local/bin/test_complete.bash && \
    chmod +x /usr/local/bin/test_complete.bash

RUN mkdir -p /etc/bash_completion.d && \
    curl --location --fail --show-error https://raw.githubusercontent.com/batect/batect-bash-completion/$BASH_COMPLETION_COMMIT_SHA/batect.bash \
    -o /etc/bash_completion.d/batect.bash
