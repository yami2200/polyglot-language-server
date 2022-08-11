#!/usr/bin/env bash
mvn compile
mvn package
cp ./target/PolyglotLanguageServer-1.0-SNAPSHOT-jar-with-dependencies.jar ./