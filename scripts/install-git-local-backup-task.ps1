param(
    [int]$IntervalMinutes = 30,
    [string]$TaskName = "Forklift ERP Git Local Backup"
)

$ErrorActionPreference = "Stop"

if ($IntervalMinutes -lt 5) {
    throw "IntervalMinutes must be at least 5."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$snapshotScript = Join-Path $PSScriptRoot "git-local-backup-snapshot.ps1"

if (-not (Test-Path $snapshotScript)) {
    throw "Cannot find backup script: $snapshotScript"
}

$taskCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$snapshotScript`""

& schtasks /Create /TN $TaskName /SC MINUTE /MO $IntervalMinutes /TR $taskCommand /F
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install scheduled task '$TaskName'."
}

Write-Host "Installed scheduled task '$TaskName'."
Write-Host "It will create local Git snapshots every $IntervalMinutes minutes."
Write-Host "Backups are stored in the local Git branch: local-backup"
