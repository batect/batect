FROM ubuntu:18.04

RUN apt-get update && apt-get install -y \
    curl \
    openjdk-8-jre \
    python3

RUN curl https://download.java.net/openjdk/jdk7u75/ri/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz --output /tmp/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz && \
    cd /usr/lib/jvm && tar xvzf /tmp/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz && \
    mv /usr/lib/jvm/java-se-7u75-ri /usr/lib/jvm/java-1.7-openjdk-amd64 && \
    rm /tmp/openjdk-7u75-b13-linux-x64-18_dec_2014.tar.gz
