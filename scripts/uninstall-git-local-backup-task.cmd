@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall-git-local-backup-task.ps1" %*
