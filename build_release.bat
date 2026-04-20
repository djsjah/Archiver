@echo off
cd /d %~dp0

call build.bat
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

if exist release rmdir /s /q release
mkdir release

jar --create --file release\encoder.jar --main-class encoder.Main -C out .
if errorlevel 1 (
    echo Failed to create encoder.jar
    pause
    exit /b 1
)

jar --create --file release\decoder.jar --main-class decoder.Main -C out .
if errorlevel 1 (
    echo Failed to create decoder.jar
    pause
    exit /b 1
)

jar --create --file release\stats.jar --main-class stats.Main -C out .
if errorlevel 1 (
    echo Failed to create stats.jar
    pause
    exit /b 1
)

echo JAR files created successfully in folder:
echo   %cd%\release
echo.
echo Files:
echo   release\encoder.jar
echo   release\decoder.jar
echo   release\stats.jar
pause