::BATCH file for windows

set BATDIR=%~dp0
set LIBDIR=%BATDIR%..\cli-app\build\libs

java -Djava.ext.dirs=%LIBDIR% org.openmuc.j60870.app.SampleServer %*
