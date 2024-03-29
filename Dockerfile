FROM ubuntu:latest

# Install prerequisites
RUN apt update
RUN apt-get -qq -y install python3                  # PYTHON3
RUN apt-get -qq -y install nodejs                   # NODE.JS
RUN apt -qq -y install openjdk-11-jre-headless      # JDK11
RUN apt -qq -y install git                          # GIT
RUN apt-get -qq -y install build-essential          # BUILD ESSENTIAL
RUN apt-get -qq -y install build-essential cmake    # CMAKE
RUN apt -qq -y install maven                        # MAVEN
RUN apt -qq -y install clang-14 --install-suggests  # CLANG
RUN apt -qq -y install default-jdk
RUN apt -qq -y install node-typescript              # NODE TYPESCRIPT
RUN apt-get -qq -y install npm                      # NPM
RUN apt-get -qq -y install python3-pip              # PIP

RUN ln -s /usr/bin/clang-14 /usr/bin/clang
RUN ln -s /usr/bin/clang++-14 /usr/bin/clang++
RUN export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64

WORKDIR /home/

# Git Clones
RUN git clone https://github.com/quentinLeDilavrec/jsitter
RUN git clone https://github.com/yami2200/tree-sitter
RUN git clone https://github.com/tree-sitter/tree-sitter-python
RUN git clone https://github.com/tree-sitter/tree-sitter-javascript
RUN git clone https://github.com/tree-sitter/tree-sitter-go
RUN git clone https://github.com/tree-sitter/tree-sitter-java
RUN git clone https://github.com/yami2200/PolyglotAST
RUN git clone --recurse-submodules https://github.com/yami2200/polyglot-language-server

WORKDIR /home/PolyglotAST/

# Setup Jsitter & PolyglotAST
RUN ./install.sh

# Setup Polyglot Language Server
WORKDIR /home/polyglot-language-server/
RUN ./install.sh