#!/bin/sh
TARGET="target/download"
mkdir -p "$TARGET"
wget "https://lanterna.googlecode.com/files/lanterna-1.0.2.jar" --directory-prefix="$TARGET"
mvn install:install-file -Dfile="$TARGET/lanterna-1.0.2.jar" -DgroupId=lanterna -DartifactId=lanterna -Dversion=1.0.2 -Dpackaging=jar
