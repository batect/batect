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
powershell.exe -ExecutionPolicy Bypass -NoLogo -File "%ps1Path%" %*
exit %ERRORLEVEL%
