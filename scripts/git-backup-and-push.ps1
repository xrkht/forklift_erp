param(
    [string]$Message,
    [string]$Remote = "origin",
    [string]$Branch,
    [switch]$Auto,
    [switch]$LocalOnly,
    [switch]$NoPush,
    [string]$LogPath
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return Join-Path $repoRoot $Path
}

function Invoke-Git {
    param([string[]]$Arguments)

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

function Invoke-GitQuiet {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & git @Arguments 2>$null
        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot
$skipPush = $LocalOnly -or $NoPush

$transcriptStarted = $false
if ($LogPath) {
    $resolvedLogPath = Resolve-RepoPath $LogPath
    $logDirectory = Split-Path -Parent $resolvedLogPath
    if ($logDirectory -and -not (Test-Path $logDirectory)) {
        New-Item -ItemType Directory -Path $logDirectory | Out-Null
    }
    Start-Transcript -Path $resolvedLogPath -Append | Out-Null
    $transcriptStarted = $true
}

if (-not (Test-Path ".git")) {
    throw "This folder is not a Git repository. Run 'git init -b main' first."
}

$lockStream = $null
$lockFile = Join-Path $repoRoot ".git\git-backup-and-push.lock"

try {
    try {
        $lockStream = [System.IO.File]::Open($lockFile, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    } catch {
        Write-Host "Another Git backup is already running. Skipping this run."
        exit 0
    }

    $gitDir = (git rev-parse --git-dir).Trim()
    $blockingStates = @(
        "MERGE_HEAD",
        "CHERRY_PICK_HEAD",
        "REVERT_HEAD",
        "rebase-apply",
        "rebase-merge"
    )

    foreach ($state in $blockingStates) {
        if (Test-Path (Join-Path $gitDir $state)) {
            throw "Git has an unfinished operation ($state). Resolve it before running backup."
        }
    }

    if (-not $Branch) {
        $Branch = (git branch --show-current).Trim()
    }

    if (-not $Branch) {
        $Branch = "main"
        Invoke-Git @("checkout", "-B", $Branch)
    }

    Invoke-Git @("add", "-A")

    $diffResult = Invoke-GitQuiet @("diff", "--cached", "--quiet")
    $hasStagedChanges = $diffResult.ExitCode -eq 1
    if ($diffResult.ExitCode -gt 1) {
        throw "Git diff failed while checking staged changes."
    }

    if ($hasStagedChanges) {
        if (-not $Message) {
            $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
            $mode = if ($Auto) { "auto" } else { "manual" }
            $Message = "backup: $mode snapshot $timestamp"
        }

        Invoke-Git @("commit", "-m", $Message)
    } else {
        Write-Host "No local changes to commit."
    }

    if ($skipPush) {
        Write-Host "Local Git backup complete. Skipping GitHub sync because local-only mode is enabled."
        exit 0
    }

    $remoteResult = Invoke-GitQuiet @("remote", "get-url", $Remote)
    if ($remoteResult.ExitCode -ne 0 -or -not $remoteResult.Output) {
        throw "Remote '$Remote' is not configured. Run: git remote add $Remote https://github.com/xrkht/forklift_erp.git"
    }

    $upstreamResult = Invoke-GitQuiet @("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
    try {
        if ($upstreamResult.ExitCode -eq 0 -and $upstreamResult.Output) {
            Invoke-Git @("pull", "--rebase", "--autostash", $Remote, $Branch)
            Invoke-Git @("push", $Remote, $Branch)
        } else {
            Invoke-Git @("push", "-u", $Remote, $Branch)
        }
    } catch {
        Write-Warning "Local Git backup is saved, but the GitHub sync failed. Check network/authentication, then rerun this script."
        throw
    }
} finally {
    if ($lockStream) {
        $lockStream.Dispose()
        Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
    }
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}
