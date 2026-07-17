@echo off
setlocal
set SCENARIO=%1
if "%SCENARIO%"=="" set SCENARIO=reconnect
set HOST=%2
if "%HOST%"=="" set HOST=localhost
set PORT=%3
if "%PORT%"=="" set PORT=12345
cd /d "%~dp0\.."

call scripts\common-java.bat
if errorlevel 1 exit /b 1

if not exist certs\client-truststore.p12 (
  echo [setup] TLS certificates not found. Generating development certificates...
  call scripts\generate-dev-certs.bat
  if errorlevel 1 exit /b 1
)

call %GRADLE_CMD% --no-daemon "-Dorg.gradle.java.home=%JAVA_HOME%" -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.fromEnv=JAVA_HOME runScenario -Pscenario=%SCENARIO% -Phost=%HOST% -Pport=%PORT%
endlocal
