@echo off
cd /d %~dp0

java -jar decoder.jar "%~1" "%~2"

if errorlevel 1 (
    echo Decoding failed.
    pause
    exit /b 1
)