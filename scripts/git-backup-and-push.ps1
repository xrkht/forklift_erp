param(
    [string]$Message,
    [string]$Remote = "origin",
    [string]$Branch
)

$ErrorActionPreference = "Stop"

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

if (-not (Test-Path ".git")) {
    throw "This folder is not a Git repository. Run 'git init -b main' first."
}

if (-not $Branch) {
    $Branch = (git branch --show-current).Trim()
}

if (-not $Branch) {
    $Branch = "main"
    Invoke-Git @("checkout", "-B", $Branch)
}

$remoteResult = Invoke-GitQuiet @("remote", "get-url", $Remote)
if ($remoteResult.ExitCode -ne 0 -or -not $remoteResult.Output) {
    throw "Remote '$Remote' is not configured. Run: git remote add $Remote https://github.com/xrkht/forklift_erp.git"
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
        $Message = "backup: manual snapshot $timestamp"
    }

    Invoke-Git @("commit", "-m", $Message)
} else {
    Write-Host "No local changes to commit."
}

$upstreamResult = Invoke-GitQuiet @("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
try {
    if ($upstreamResult.ExitCode -eq 0 -and $upstreamResult.Output) {
        Invoke-Git @("push", $Remote, $Branch)
    } else {
        Invoke-Git @("push", "-u", $Remote, $Branch)
    }
} catch {
    Write-Warning "Local Git backup is saved, but the GitHub push failed. Check network/authentication or pull the remote history before pushing again."
    throw
}
