@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo MBOX TV - DIRECT APK BUILD
echo ==========================================
echo.

if exist gradlew.bat goto BUILD

echo Gradle Wrapper files are not fully included in this source package.
echo Open the project once in Android Studio and let it sync, then use:
echo Build ^> Build APK(s)
echo Variant: directDebug or directRelease
echo.
pause
exit /b 1

:BUILD
call gradlew.bat assembleDirectDebug
if errorlevel 1 (
  echo BUILD FAILED
  pause
  exit /b 1
)

echo.
echo APK ready in:
echo app\build\outputs\apk\direct\debug\
explorer app\build\outputs\apk\direct\debug\
pause
