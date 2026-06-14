[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$mvnw = Join-Path $repoRoot "mvnw.cmd"

function Test-Jdk21Home {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }

    $java = Join-Path $Path "bin\java.exe"
    if (-not (Test-Path $java)) {
        return $false
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionOutput = & $java -version 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return $exitCode -eq 0 -and (($versionOutput -join "`n") -match 'version "21\.')
}

$candidates = New-Object System.Collections.Generic.List[string]
if ($env:JAVA_HOME) {
    $candidates.Add($env:JAVA_HOME)
}

$patterns = @(
    (Join-Path $env:USERPROFILE ".jdks\*21*"),
    "C:\Program Files\Eclipse Adoptium\jdk-21*",
    "C:\Program Files\Microsoft\jdk-21*",
    "C:\Program Files\Java\jdk-21*"
)

foreach ($pattern in $patterns) {
    Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
}

$javaHome = $null
foreach ($candidate in $candidates) {
    if (Test-Jdk21Home $candidate) {
        $javaHome = $candidate
        break
    }
}

if (-not $javaHome) {
    throw "JDK 21 was not found. Install JDK 21 or set JAVA_HOME before running Maven."
}

$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome "bin") + [IO.Path]::PathSeparator + $env:Path

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    $MavenArgs = @("verify")
}

& $mvnw @MavenArgs
exit $LASTEXITCODE
