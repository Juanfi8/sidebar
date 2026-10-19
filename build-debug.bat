@echo off
setlocal

cd /d "%~dp0"

echo [1/4] Removing stale node_modules and lockfile...
if exist node_modules rmdir /s /q node_modules
if exist package-lock.json del /q package-lock.json

echo [2/4] Installing JS dependencies...
call npm install
if errorlevel 1 (echo npm install failed & exit /b 1)

echo [3/4] Cleaning Gradle build cache...
cd android
if exist app\.cxx rmdir /s /q app\.cxx
call gradlew.bat clean
if errorlevel 1 (echo Gradle clean failed & exit /b 1)

echo [4/4] Building debug APK (JS bundled in)...
call gradlew.bat assembleRelease
if errorlevel 1 (echo Gradle build failed & exit /b 1)

echo.
echo Done. APK:
echo   android\app\build\outputs\apk\debug\app-debug.apk

endlocal
