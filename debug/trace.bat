@echo off

jre\win64\bin\jcmd.exe "bin\bootstrap.jar" Thread.print > log\strack_trace.log