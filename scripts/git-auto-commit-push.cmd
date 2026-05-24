@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0git-auto-commit-push.ps1" %*
