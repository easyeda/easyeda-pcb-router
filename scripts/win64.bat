@echo off

jre\win64\bin\java.exe -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin\bootstrap.jar 2>> log\router.log
