param(
    [string]$TaskName = "Forklift ERP Git Local Backup"
)

$ErrorActionPreference = "Stop"

& schtasks /Query /TN $TaskName | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Scheduled task '$TaskName' is not installed."
    exit 0
}

& schtasks /Delete /TN $TaskName /F
if ($LASTEXITCODE -ne 0) {
    throw "Failed to remove scheduled task '$TaskName'."
}

Write-Host "Removed scheduled task '$TaskName'."
