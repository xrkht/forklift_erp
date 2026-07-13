[CmdletBinding()]
param(
    [ValidatePattern('^[0-9A-Za-z][0-9A-Za-z._-]*$')]
    [string]$Version = (Get-Date -Format 'yyyyMMdd-HHmmss'),

    [ValidatePattern('^[a-z0-9._/-]+$')]
    [string]$ImageName = 'forklift-erp',

    [ValidateSet('linux/amd64', 'linux/arm64')]
    [string]$Platform = 'linux/amd64',

    [string]$OutputDirectory = 'dist',

    [switch]$SkipTests,
    [switch]$SkipQualityCheck,
    [switch]$Push,
    [switch]$NoExport
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Invoke-Checked {
    param(
        [Parameter(Mandatory)]
        [string]$Command,
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    Write-Host "`n> $Command $($Arguments -join ' ')" -ForegroundColor Cyan
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command"
    }
}

if (-not (Test-Path -LiteralPath '.\mvnw.cmd')) {
    throw 'mvnw.cmd was not found. Run this script from the project checkout.'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI was not found. Install/start Docker Desktop before building an image.'
}

$mavenArguments = @('clean', 'package')
if ($SkipTests) {
    $mavenArguments += '-DskipTests'
}
Invoke-Checked -Command '.\mvnw.cmd' -Arguments $mavenArguments

if (-not $SkipQualityCheck) {
    Invoke-Checked -Command 'java' -Arguments @('scripts/CheckCodeQuality.java')
}

$applicationJar = Get-ChildItem -LiteralPath 'target' -Filter 'forklift-erp-*.jar' -File |
    Where-Object { $_.Name -notlike '*.original' } |
    Select-Object -First 1
if (-not $applicationJar) {
    throw 'The executable Spring Boot JAR was not produced under target.'
}

$dockerInputDirectory = Join-Path $projectRoot 'target/docker'
New-Item -ItemType Directory -Path $dockerInputDirectory -Force | Out-Null
$dockerJar = Join-Path $dockerInputDirectory 'app.jar'
Copy-Item -LiteralPath $applicationJar.FullName -Destination $dockerJar -Force

$imageReference = "${ImageName}:${Version}"
$buildArguments = @(
    'buildx', 'build',
    '--platform', $Platform,
    '--tag', $imageReference,
    '--tag', "${ImageName}:latest"
)
if ($Push) {
    $buildArguments += '--push'
} else {
    $buildArguments += '--load'
}
$buildArguments += '.'
Invoke-Checked -Command 'docker' -Arguments $buildArguments

$safePlatform = $Platform.Replace('/', '-')
$releaseDirectory = Join-Path $projectRoot (Join-Path $OutputDirectory "forklift-erp-${Version}")
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
Copy-Item -LiteralPath 'deploy/synology/compose.yaml' -Destination $releaseDirectory -Force
$releaseEnvExample = Join-Path $releaseDirectory '.env.example'
Copy-Item -LiteralPath 'deploy/synology/.env.example' -Destination $releaseEnvExample -Force
$releaseEnvLines = Get-Content -LiteralPath $releaseEnvExample | ForEach-Object {
    if ($_ -like 'ERP_VERSION=*') { "ERP_VERSION=$Version" } else { $_ }
}
Set-Content -LiteralPath $releaseEnvExample -Value $releaseEnvLines -Encoding ascii
Copy-Item -LiteralPath 'deploy/synology/README.md' -Destination $releaseDirectory -Force
Copy-Item -LiteralPath 'deploy/synology/update.sh' -Destination $releaseDirectory -Force
Copy-Item -LiteralPath $applicationJar.FullName -Destination (Join-Path $releaseDirectory $applicationJar.Name) -Force

$checksumFiles = @((Join-Path $releaseDirectory $applicationJar.Name))
if (-not $Push -and -not $NoExport) {
    $imageArchive = Join-Path $releaseDirectory "forklift-erp-${Version}-${safePlatform}.tar"
    Invoke-Checked -Command 'docker' -Arguments @('save', '--output', $imageArchive, $imageReference)
    $checksumFiles += $imageArchive
}

$checksumPath = Join-Path $releaseDirectory 'SHA256SUMS.txt'
$checksumLines = foreach ($file in $checksumFiles) {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $file
    "{0}  {1}" -f $hash.Hash.ToLowerInvariant(), (Split-Path -Leaf $file)
}
Set-Content -LiteralPath $checksumPath -Value $checksumLines -Encoding ascii

Write-Host "`nRelease completed." -ForegroundColor Green
Write-Host "Image:   $imageReference"
Write-Host "Platform: $Platform"
Write-Host "Output:  $releaseDirectory"
if ($Push) {
    Write-Host 'The image was pushed. Set ERP_IMAGE and ERP_VERSION on the NAS, then pull/recreate the app service.'
} elseif ($NoExport) {
    Write-Host 'The image is available in the local Docker image store; no TAR archive was created.'
} else {
    Write-Host 'Import the generated TAR in Synology Container Manager before starting the Compose project.'
}
