param(
    [int]$IntervalMinutes = 10,
    [string]$TaskName = "Forklift ERP Git Auto Backup"
)

$ErrorActionPreference = "Stop"

if ($IntervalMinutes -lt 1) {
    throw "IntervalMinutes must be at least 1."
}

$backupScript = Resolve-Path (Join-Path $PSScriptRoot "git-backup-and-push.ps1")
$startTime = (Get-Date).AddMinutes(1).ToString("HH:mm")
$taskCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$backupScript`" -Auto -LocalOnly -LogPath `"logs\git-auto-backup.log`""

$arguments = @(
    "/Create",
    "/TN", $TaskName,
    "/TR", $taskCommand,
    "/SC", "MINUTE",
    "/MO", "$IntervalMinutes",
    "/ST", $startTime,
    "/F"
)

& schtasks.exe @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create scheduled task '$TaskName'."
}

Write-Host "Installed scheduled task '$TaskName'. It runs every $IntervalMinutes minute(s)."
