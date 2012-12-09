#!/bin/sh
mvn clean install assembly:single && java -jar target/stein-*-jar-with-dependencies.jar
