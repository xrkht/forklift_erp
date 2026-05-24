@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0git-local-backup-snapshot.ps1" %*
