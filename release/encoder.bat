@echo off
cd /d %~dp0

java -jar encoder.jar "%~1" "%~2"

if errorlevel 1 (
    echo Encoding failed.
    pause
    exit /b 1
)