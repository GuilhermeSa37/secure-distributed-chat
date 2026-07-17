@echo off
setlocal
set PORT=%1
if "%PORT%"=="" set PORT=12345
cd /d "%~dp0\.."

call scripts\common-java.bat
if errorlevel 1 exit /b 1

if not exist certs\server-keystore.p12 (
  echo [setup] TLS server keystore not found. Generating development certificates...
  call scripts\generate-dev-certs.bat
  if errorlevel 1 exit /b 1
)

call %GRADLE_CMD% --no-daemon "-Dorg.gradle.java.home=%JAVA_HOME%" -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.fromEnv=JAVA_HOME runServer -Pport=%PORT%
endlocal
