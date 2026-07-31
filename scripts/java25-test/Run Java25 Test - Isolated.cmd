@echo off
setlocal

set "KIT_ROOT=%~dp0.."
set "BRIDGE_EXE=%KIT_ROOT%\Portable\MTGO Twitch Bridge\MTGO Twitch Bridge.exe"
if not exist "%BRIDGE_EXE%" (
  echo Java 25 bridge executable was not found:
  echo %BRIDGE_EXE%
  pause
  exit /b 1
)

set "APPDATA=%KIT_ROOT%\test-appdata"
set "BRIDGE_PUBLISH_TOKEN="
set "SUPABASE_RELAY_FUNCTION_URL="

if not exist "%APPDATA%" mkdir "%APPDATA%"
start "" "%BRIDGE_EXE%"
