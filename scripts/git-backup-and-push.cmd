@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0git-backup-and-push.ps1" %*
