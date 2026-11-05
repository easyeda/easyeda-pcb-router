@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin\bootstrap.jar
) else if exist jre\win64\bin\java.exe (
    jre\win64\bin\java.exe -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin\bootstrap.jar
) else (
    java -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin\bootstrap.jar
)
endlocal
