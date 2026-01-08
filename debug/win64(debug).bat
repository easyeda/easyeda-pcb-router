@echo off

jre\win64\bin\java.exe -verbose -XX:+PrintGC -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin\bootstrap.jar > log\debug.log 2> log\debug.err.log
