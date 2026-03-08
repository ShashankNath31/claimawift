@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "LOG_DIR=%ROOT%\run-logs\service-logs"
set "MAVEN_CMD=%ROOT%\apache-maven-3.9.13\bin\mvn.cmd"

if "%~1"=="" goto usage

if /I "%~1"=="start" goto start_all
if /I "%~1"=="stop" goto stop_all
if /I "%~1"=="restart" goto restart_all

goto usage

:usage
echo Usage:
echo   dev-stack.cmd start
echo   dev-stack.cmd stop
echo   dev-stack.cmd restart
exit /b 1

:ensure_log_dir
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1
exit /b 0

:ensure_maven
if exist "%MAVEN_CMD%" exit /b 0
echo ERROR: Maven not found at "%MAVEN_CMD%"
echo Update MAVEN_CMD in dev-stack.cmd if your Maven path changed.
exit /b 1

:kill_port
set "TARGET_PORT=%~1"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%TARGET_PORT% .*LISTENING"') do (
  echo Stopping PID %%P on port %TARGET_PORT% ...
  taskkill /PID %%P /F >nul 2>&1
)
exit /b 0

:wait_port
set "WAIT_PORT=%~1"
set /a "MAX_TRIES=%~2"
:wait_loop
set "IS_LISTENING="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%WAIT_PORT% .*LISTENING"') do (
  set "IS_LISTENING=1"
)
if defined IS_LISTENING exit /b 0
set /a "MAX_TRIES-=1"
if %MAX_TRIES% LEQ 0 exit /b 1
timeout /t 2 >nul
goto wait_loop

:start_service
set "SERVICE_NAME=%~1"
echo Starting %SERVICE_NAME% ...
start "%SERVICE_NAME%" cmd /c "cd /d ""%ROOT%"" && ""%MAVEN_CMD%"" -pl %SERVICE_NAME% spring-boot:run > ""%LOG_DIR%\%SERVICE_NAME%.out.log"" 2>&1"
exit /b 0

:start_frontend
echo Starting frontend-angular ...
start "frontend-angular" cmd /c "cd /d ""%ROOT%\frontend-angular"" && npm start > ""%LOG_DIR%\frontend-angular.out.log"" 2>&1"
exit /b 0

:stop_all
echo Stopping frontend and backend ports ...
call :kill_port 4200
call :kill_port 8080
call :kill_port 8081
call :kill_port 8082
call :kill_port 8083
call :kill_port 8084
call :kill_port 8085
call :kill_port 8086
call :kill_port 8087
call :kill_port 8761
call :kill_port 8888
echo Stop complete.
exit /b 0

:start_all
call :ensure_maven
if errorlevel 1 exit /b 1
call :ensure_log_dir

call :start_service config-server
call :wait_port 8888 90
if errorlevel 1 (
  echo ERROR: config-server did not bind to port 8888 in time.
  exit /b 1
)

call :start_service eureka-server
call :wait_port 8761 90
if errorlevel 1 (
  echo ERROR: eureka-server did not bind to port 8761 in time.
  exit /b 1
)

call :start_service auth-service
call :wait_port 8081 90
if errorlevel 1 (
  echo ERROR: auth-service did not bind to port 8081 in time.
  exit /b 1
)

call :start_service claim-service
call :wait_port 8082 90
if errorlevel 1 (
  echo ERROR: claim-service did not bind to port 8082 in time.
  exit /b 1
)

call :start_service document-service
call :wait_port 8083 90
if errorlevel 1 (
  echo ERROR: document-service did not bind to port 8083 in time.
  exit /b 1
)

call :start_service assessment-service
call :wait_port 8084 90
if errorlevel 1 (
  echo ERROR: assessment-service did not bind to port 8084 in time.
  exit /b 1
)

call :start_service payment-service
call :wait_port 8085 90
if errorlevel 1 (
  echo ERROR: payment-service did not bind to port 8085 in time.
  exit /b 1
)

call :start_service notification-service
call :wait_port 8086 90
if errorlevel 1 (
  echo ERROR: notification-service did not bind to port 8086 in time.
  exit /b 1
)

call :start_service reporting-service
call :wait_port 8087 90
if errorlevel 1 (
  echo ERROR: reporting-service did not bind to port 8087 in time.
  exit /b 1
)

call :start_service api-gateway
call :wait_port 8080 90
if errorlevel 1 (
  echo ERROR: api-gateway did not bind to port 8080 in time.
  exit /b 1
)

call :start_frontend
call :wait_port 4200 120
if errorlevel 1 (
  echo WARNING: frontend did not bind to port 4200 in time. Check "%LOG_DIR%\frontend-angular.out.log"
)

echo All start commands launched. Logs: "%LOG_DIR%"
exit /b 0

:restart_all
call :stop_all
timeout /t 2 >nul
call :start_all
exit /b %errorlevel%
