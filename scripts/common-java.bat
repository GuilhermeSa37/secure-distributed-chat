@echo off
rem Shared Java/Gradle helper variables for Windows run scripts.
rem Call this file from the project root with: call scripts\common-java.bat

where javac >nul 2>nul
if errorlevel 1 (
  echo [error] Java 21 JDK is required, but javac was not found.
  echo [error] Install a JDK, not only a JRE.
  echo [error] Recommended: install a Java 21+ JDK and make sure javac is on PATH.
  exit /b 1
)

for /f "delims=" %%I in ('where javac') do (
  set "JAVAC_PATH=%%I"
  goto :found_javac
)

:found_javac
for %%I in ("%JAVAC_PATH%\..\..") do set "JAVA_HOME=%%~fI"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [setup] Using Java JDK: %JAVA_HOME%

if exist gradlew.bat (
  set "GRADLE_CMD=gradlew.bat"
) else (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo [error] Gradle wrapper gradlew.bat was not found and system gradle is not installed.
    exit /b 1
  )
  set "GRADLE_CMD=gradle"
)
exit /b 0
