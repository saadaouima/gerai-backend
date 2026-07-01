@echo off
cd /d "c:\Users\marie\Documents\GRH_PFE\gerai-backend\gerai\demandes-service"
call .\mvnw.cmd package -Dmaven.test.skip=true
if %ERRORLEVEL% NEQ 0 ( echo BUILD FAILED & pause & exit /b 1 )
java -jar target\demandes-service-0.0.1-SNAPSHOT.jar
