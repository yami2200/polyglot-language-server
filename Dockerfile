FROM ubuntu:latest
# Install prerequisites
RUN apt update
RUN apt-get -qq -y install python3
RUN apt-get -qq -y install nodejs
RUN apt -qq -y install openjdk-11-jre-headless
RUN apt -qq -y install git
