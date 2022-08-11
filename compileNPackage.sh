#!/usr/bin/env bash

# Compile & Package the language server

mvn compile
mvn package
cp ./target/PolyglotLanguageServer-1.0-SNAPSHOT-jar-with-dependencies.jar ./