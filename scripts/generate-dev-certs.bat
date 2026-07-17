@echo off
setlocal

cd /d "%~dp0\.."

set CERT_DIR=%1
set PASSWORD=%2
if "%CERT_DIR%"=="" set CERT_DIR=certs
if "%PASSWORD%"=="" set PASSWORD=%CHAT_TLS_PASSWORD%
if "%PASSWORD%"=="" set PASSWORD=local-development-only
set SERVER_ALIAS=chat-server
set DISTINGUISHED_NAME=CN=localhost, OU=Development, O=Secure Distributed Chat, L=Local, ST=Local, C=PT
if not "%CHAT_TLS_DNAME%"=="" set DISTINGUISHED_NAME=%CHAT_TLS_DNAME%

where keytool >nul 2>nul
if errorlevel 1 (
  echo keytool was not found. Install/use a JDK 21 distribution, not only a JRE.
  exit /b 1
)

if not exist "%CERT_DIR%" mkdir "%CERT_DIR%"
del /Q "%CERT_DIR%\server-keystore.p12" 2>nul
del /Q "%CERT_DIR%\client-truststore.p12" 2>nul
del /Q "%CERT_DIR%\server-cert.pem" 2>nul

keytool -genkeypair ^
  -alias %SERVER_ALIAS% ^
  -keyalg RSA ^
  -keysize 3072 ^
  -sigalg SHA256withRSA ^
  -validity 365 ^
  -dname "%DISTINGUISHED_NAME%" ^
  -ext "SAN=dns:localhost,ip:127.0.0.1" ^
  -keystore "%CERT_DIR%\server-keystore.p12" ^
  -storetype PKCS12 ^
  -storepass "%PASSWORD%" ^
  -keypass "%PASSWORD%"

if errorlevel 1 exit /b 1

keytool -exportcert ^
  -alias %SERVER_ALIAS% ^
  -keystore "%CERT_DIR%\server-keystore.p12" ^
  -storetype PKCS12 ^
  -storepass "%PASSWORD%" ^
  -rfc ^
  -file "%CERT_DIR%\server-cert.pem"

if errorlevel 1 exit /b 1

keytool -importcert ^
  -noprompt ^
  -alias %SERVER_ALIAS% ^
  -file "%CERT_DIR%\server-cert.pem" ^
  -keystore "%CERT_DIR%\client-truststore.p12" ^
  -storetype PKCS12 ^
  -storepass "%PASSWORD%"

if errorlevel 1 exit /b 1

echo Generated local development TLS files:
echo   %CERT_DIR%\server-keystore.p12
echo   %CERT_DIR%\server-cert.pem
echo   %CERT_DIR%\client-truststore.p12
