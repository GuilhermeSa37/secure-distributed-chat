@echo off
setlocal enabledelayedexpansion

REM Optional helper for AI rooms.
REM Starts a local Ollama server if one is not already reachable and ensures the model exists.
REM This script deliberately avoids sudo/Docker so the normal server startup stays portable.

set MODEL=%~1
if "%MODEL%"=="" set MODEL=llama3

where ollama >nul 2>nul
if errorlevel 1 (
  echo [ollama] Ollama CLI not found. Install Ollama first: https://ollama.com/download
  exit /b 1
)

ollama list >nul 2>nul
if errorlevel 1 (
  echo [ollama] Starting Ollama server in background...
  if not exist .run mkdir .run
  start "Ollama Server" /min cmd /c "ollama serve > .run\ollama.log 2>&1"

  set READY=0
  for /L %%i in (1,1,30) do (
    ollama list >nul 2>nul
    if not errorlevel 1 (
      set READY=1
      goto :server_ready
    )
    timeout /t 1 /nobreak >nul
  )

  :server_ready
  if not "!READY!"=="1" (
    echo [ollama] Could not start Ollama. Check .run\ollama.log
    exit /b 1
  )
) else (
  echo [ollama] Ollama server is already running.
)

if "%NO_PULL%"=="1" (
  echo [ollama] Skipping model check/pull because NO_PULL=1.
) else (
  ollama list | findstr /B /C:"%MODEL%" >nul 2>nul
  if errorlevel 1 (
    echo [ollama] Pulling model '%MODEL%'... This can take a while the first time.
    ollama pull "%MODEL%"
    if errorlevel 1 exit /b 1
  ) else (
    echo [ollama] Model '%MODEL%' is already available.
  )
)

echo [ollama] Ready at http://localhost:11434 using model '%MODEL%'.
echo [ollama] Now start the chat server with: scripts\run-server.bat 12345
