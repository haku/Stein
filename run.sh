#!/bin/sh
set -eu
mvn clean install assembly:single
exec java -jar target/stein-*-jar-with-dependencies.jar
