#!/usr/bin/env sh
cd "`dirname $0`"
cd ..
cd ui
mkdocs build
cd ..
./gradlew assemble
