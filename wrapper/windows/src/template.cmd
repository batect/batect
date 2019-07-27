@echo off
rem This file is part of batect.
rem Do not modify this file, it will be overwritten next time you upgrade batect.
rem You should commit this file to version control alongside the rest of your project. It should not be installed globally.
rem For more information, visit https://github.com/charleskorn/batect.

setlocal EnableDelayedExpansion

set "version=VERSION-GOES-HERE"

if "%BATECT_CACHE_DIR%" == "" (
    set "BATECT_CACHE_DIR=%USERPROFILE%\.batect\cache"
)

set "rootCacheDir=!BATECT_CACHE_DIR!"
set "cacheDir=%rootCacheDir%\%version%"
set "ps1Path=%cacheDir%\batect-%version%.ps1"

set script=POWERSHELL-SCRIPT-GOES-HERE

if not exist "%cacheDir%" (
    mkdir "%cacheDir%"
)

echo !script! > "%ps1Path%"

set BATECT_WRAPPER_SCRIPT_DIR=%~dp0

rem Why do we explicitly exit?
rem cmd.exe appears to read this script one line at a time and then executes it.
rem If we modify the script while it is still running (eg. because we're updating it), then cmd.exe does all kinds of odd things
rem because it continues execution from the next byte (which was previously the end of the line).
rem By explicitly exiting on the same line as starting the application, we avoid these issues as cmd.exe has already read the entire
rem line before we start the application and therefore will always exit.
powershell.exe -ExecutionPolicy Bypass -NoLogo -NoProfile -File "%ps1Path%" %* && exit 0 || exit !ERRORLEVEL!

rem What's this for?
rem This is so the tests for the wrapper has a way to ensure that the line above terminates the script correctly.
echo WARNING: you should never see this, and if you do, then batect's wrapper script has a bug
