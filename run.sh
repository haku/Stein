#!/bin/sh
mvn install && mvn exec:java -Dexec.mainClass="com.vaguehope.stein.Main"
