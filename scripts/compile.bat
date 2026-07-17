@echo off
setlocal
cd /d "%~dp0\.."
call scripts\common-java.bat
if errorlevel 1 exit /b 1
call %GRADLE_CMD% --no-daemon "-Dorg.gradle.java.home=%JAVA_HOME%" -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.fromEnv=JAVA_HOME build
endlocal
