param(
    [string]$TaskName = "Forklift ERP Git Auto Backup"
)

$ErrorActionPreference = "Stop"

& schtasks.exe /Delete /TN $TaskName /F
if ($LASTEXITCODE -ne 0) {
    throw "Failed to delete scheduled task '$TaskName'."
}

Write-Host "Deleted scheduled task '$TaskName'."
