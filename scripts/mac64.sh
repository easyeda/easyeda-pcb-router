#! /bin/sh

chmod 755 jre/mac64/bin/java

jre/mac64/bin/java -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin/bootstrap.jar 2>> log/router.log
