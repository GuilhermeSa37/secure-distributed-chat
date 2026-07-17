@echo off
setlocal
set HOST=%1
set PORT=%2
if "%HOST%"=="" set HOST=localhost
if "%PORT%"=="" set PORT=12345
cd /d "%~dp0\.."

call scripts\common-java.bat
if errorlevel 1 exit /b 1

if not exist certs\client-truststore.p12 (
  echo [setup] TLS client truststore not found. Generating development certificates...
  call scripts\generate-dev-certs.bat
  if errorlevel 1 exit /b 1
)

call %GRADLE_CMD% --no-daemon "-Dorg.gradle.java.home=%JAVA_HOME%" -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.fromEnv=JAVA_HOME runClient -Phost=%HOST% -Pport=%PORT%
endlocal
