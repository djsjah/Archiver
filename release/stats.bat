@echo off
cd /d %~dp0

java -jar stats.jar "%~1"

if errorlevel 1 (
    echo Stats calculation failed.
    pause
    exit /b 1
)

pause