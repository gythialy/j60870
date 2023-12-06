::BATCH file for windows
@echo off
set BATDIR=%~dp0
set CLIDIR=%BATDIR%..\cli-app\build\libs\*
set LIBDIR=%BATDIR%..\build\libs\*
@echo on

java -cp "%LIBDIR%;%CLIDIR%" org.openmuc.j60870.app.SampleServer %*
