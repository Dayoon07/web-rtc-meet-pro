@echo off
rem App build (Android APK + Windows exe) into dist\ . Details: deploy\app-build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app-build.ps1" %*
