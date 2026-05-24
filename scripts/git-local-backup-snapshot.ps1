param(
    [string]$BackupRef = "local-backup",
    [string]$Message
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

function Invoke-GitText {
    param([string[]]$Arguments)

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }

    return ($output -join "`n").Trim()
}

function Resolve-GitPath {
    param([string]$RelativeGitPath)

    $path = Invoke-GitText @("rev-parse", "--git-path", $RelativeGitPath)
    if ([System.IO.Path]::IsPathRooted($path)) {
        return $path
    }

    return Join-Path $repoRoot $path
}

function Write-BackupMessage {
    param([string]$Text)

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] $Text"
    Write-Host $line

    if ($script:LogPath) {
        New-Item -ItemType Directory -Force (Split-Path $script:LogPath) | Out-Null
        Add-Content -LiteralPath $script:LogPath -Value $line -Encoding UTF8
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

if (-not (Test-Path ".git")) {
    throw "This folder is not a Git repository."
}

$script:LogPath = Join-Path $repoRoot "logs\git-local-backup.log"

if ($BackupRef -notmatch "^refs/") {
    $BackupRef = "refs/heads/$BackupRef"
}

$backupName = $BackupRef -replace "^refs/heads/", ""
$lockPath = Resolve-GitPath "local-backup.lock"
$indexPath = Resolve-GitPath "local-backup-index"
$lockStream = $null
$previousIndexFile = $env:GIT_INDEX_FILE
$treeCreated = $false

try {
    try {
        $lockStream = [System.IO.File]::Open(
            $lockPath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None
        )
    } catch [System.IO.IOException] {
        Write-BackupMessage "Another local backup is already running. Skipped."
        exit 0
    }

    Remove-Item -LiteralPath $indexPath -Force -ErrorAction SilentlyContinue
    $env:GIT_INDEX_FILE = $indexPath

    $headResult = Invoke-GitQuiet @("rev-parse", "--verify", "HEAD")
    $hasHead = $headResult.ExitCode -eq 0 -and $headResult.Output
    $headCommit = ""

    if ($hasHead) {
        $headCommit = ($headResult.Output -join "`n").Trim()
        Invoke-Git @("read-tree", $headCommit)
    } else {
        Invoke-Git @("read-tree", "--empty")
    }

    $addResult = Invoke-GitQuiet @("add", "-A", "--", ".")
    if ($addResult.ExitCode -ne 0) {
        throw "Git command failed: git add -A -- ."
    }

    $treeId = Invoke-GitText @("write-tree")
    $treeCreated = $true
} finally {
    if ($previousIndexFile) {
        $env:GIT_INDEX_FILE = $previousIndexFile
    } else {
        Remove-Item Env:\GIT_INDEX_FILE -ErrorAction SilentlyContinue
    }

    Remove-Item -LiteralPath $indexPath -Force -ErrorAction SilentlyContinue

    if (-not $treeCreated) {
        if ($lockStream) {
            $lockStream.Close()
        }

        Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
    }
}

try {
    $backupResult = Invoke-GitQuiet @("rev-parse", "--verify", $BackupRef)
    $hasBackup = $backupResult.ExitCode -eq 0 -and $backupResult.Output
    $backupCommit = ""
    $parents = @()

    if ($hasBackup) {
        $backupCommit = ($backupResult.Output -join "`n").Trim()
        $previousTree = Invoke-GitText @("rev-parse", "$BackupRef^{tree}")

        if ($treeId -eq $previousTree) {
            Write-BackupMessage "No changes to back up. '$backupName' already matches the working tree."
            exit 0
        }

        $parents += @("-p", $backupCommit)
    } elseif ($hasHead) {
        $headTree = Invoke-GitText @("rev-parse", "HEAD^{tree}")
        if ($treeId -eq $headTree) {
            Invoke-Git @("update-ref", $BackupRef, $headCommit)
            Write-BackupMessage "Initialized '$backupName' at current HEAD $($headCommit.Substring(0, 7))."
            exit 0
        }

        $parents += @("-p", $headCommit)
    }

    if (-not $Message) {
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $Message = "backup: local snapshot $timestamp"
    }

    $commitArgs = @("commit-tree", $treeId) + $parents + @("-m", $Message)
    $newCommit = Invoke-GitText $commitArgs
    Invoke-Git @("update-ref", $BackupRef, $newCommit)

    Write-BackupMessage "Created local backup $($newCommit.Substring(0, 7)) on '$backupName'."
} finally {
    if ($lockStream) {
        $lockStream.Close()
    }

    Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
}
