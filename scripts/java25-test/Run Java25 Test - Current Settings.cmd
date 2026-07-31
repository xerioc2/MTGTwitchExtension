@echo off
setlocal

set "BRIDGE_EXE=%~dp0..\Portable\MTGO Twitch Bridge\MTGO Twitch Bridge.exe"
if not exist "%BRIDGE_EXE%" (
  echo Java 25 bridge executable was not found:
  echo %BRIDGE_EXE%
  pause
  exit /b 1
)

start "" "%BRIDGE_EXE%"
