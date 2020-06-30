FROM ubuntu:20.04

RUN apt-get update && apt-get install -y \
    curl \
    openjdk-8-jre-headless \
    openjdk-11-jre-headless \
    python3 \
    unzip

RUN curl --show-error --retry 3 --retry-connrefused https://download.oracle.com/openjdk/jdk7u75/ri/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz --output /tmp/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz && \
    cd /usr/lib/jvm && tar xzf /tmp/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz && \
    mv /usr/lib/jvm/java-se-7u75-ri /usr/lib/jvm/java-7-openjdk-amd64 && \
    rm /tmp/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz

RUN curl --show-error --retry 3 --retry-connrefused https://download.oracle.com/openjdk/jdk9/ri/openjdk-9+181_linux-x64_ri.zip --output /tmp/openjdk-9+181_linux-x64_ri.zip && \
    cd /usr/lib/jvm && unzip -q /tmp/openjdk-9+181_linux-x64_ri.zip && \
    mv /usr/lib/jvm/java-se-9-ri/jdk-9 /usr/lib/jvm/java-9-openjdk-amd64 && \
    rm /tmp/openjdk-9+181_linux-x64_ri.zip

RUN curl --show-error --retry 3 --retry-connrefused https://download.oracle.com/java/GA/jdk10/10.0.1/fb4372174a714e6b8c52526dc134031e/10//openjdk-10.0.1_linux-x64_bin.tar.gz --output /tmp/openjdk-10.0.1_linux-x64_bin.tar.gz && \
    cd /usr/lib/jvm && tar xzf /tmp/openjdk-10.0.1_linux-x64_bin.tar.gz && \
    mv /usr/lib/jvm/jdk-10.0.1 /usr/lib/jvm/java-10-openjdk-amd64 && \
    rm /tmp/openjdk-10.0.1_linux-x64_bin.tar.gz
