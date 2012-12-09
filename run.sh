#!/bin/sh
mvn clean install && mvn exec:java -Dexec.mainClass="com.vaguehope.stein.Main"
