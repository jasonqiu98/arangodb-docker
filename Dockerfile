FROM arangodb:3.9.2

RUN apk add maven

RUN cd /root \ 
  && wget https://corretto.aws/downloads/latest/amazon-corretto-17-x64-alpine-jdk.tar.gz \
  && tar -zxvf /root/amazon-corretto-17-x64-alpine-jdk.tar.gz \
  && rm amazon-corretto-17-x64-alpine-jdk.tar.gz \
  && cd /

ENV JAVA_HOME=/root/amazon-corretto-17.0.4.9.1-alpine-linux-x64
ENV PATH=$JAVA_HOME/bin:$PATH