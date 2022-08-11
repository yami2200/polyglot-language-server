#!/usr/bin/env bash

cd javascript-typescript-langserver/
npm install
npm run build
cd ../python-language-server
pip install 'python-language-server[all]'
cd ..
mvn clean install -U
mvn compile
mvn test
./compileNPackage.sh